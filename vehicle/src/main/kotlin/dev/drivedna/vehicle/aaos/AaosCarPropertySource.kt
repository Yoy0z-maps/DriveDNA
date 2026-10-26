package dev.drivedna.vehicle.aaos

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import dev.drivedna.core.domain.model.CapabilityStatus
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.VehicleCapability
import dev.drivedna.core.domain.model.VehicleSample
import dev.drivedna.core.domain.model.VehicleSignal
import dev.drivedna.core.domain.model.gearFromAaos
import dev.drivedna.core.domain.model.metersPerSecondToKph
import dev.drivedna.vehicle.VehicleDataSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Reads PERF_VEHICLE_SPEED (m/s, ~10 Hz on the Pleos emulator) and GEAR_SELECTION through
 * the AAOS CarPropertyManager. Verified by the Phase 1 probe.
 */
class AaosCarPropertySource(private val context: Context) : VehicleDataSource {
    override val source = DataSource.LIVE

    override suspend fun capabilities(): Set<VehicleCapability> {
        val speedGranted = context.checkSelfPermission(SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED
        val car = Car.createCar(context) ?: return unknownAll("Car service unavailable")
        return try {
            val cpm = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            setOf(
                capability(VehicleSignal.SPEED, cpm, VehiclePropertyIds.PERF_VEHICLE_SPEED, speedGranted),
                capability(VehicleSignal.GEAR, cpm, VehiclePropertyIds.GEAR_SELECTION, permissionGranted = true),
            )
        } finally {
            car.disconnect()
        }
    }

    override fun samples(tripId: Long): Flow<VehicleSample> = callbackFlow {
        val car = Car.createCar(context)
        val cpm = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                if (value.status != CarPropertyValue.STATUS_AVAILABLE) return
                val now = toEpochMs(value.timestamp)
                val sample = when (value.propertyId) {
                    VehiclePropertyIds.PERF_VEHICLE_SPEED ->
                        VehicleSample(tripId, now, metersPerSecondToKph(value.value as Float), null, null, DataSource.LIVE)
                    VehiclePropertyIds.GEAR_SELECTION ->
                        VehicleSample(tripId, now, null, null, gearFromAaos(value.value as Int), DataSource.LIVE)
                    else -> return
                }
                trySend(sample)
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) = Unit
        }
        cpm.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FASTEST)
        cpm.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        awaitClose {
            cpm.unregisterCallback(callback)
            car.disconnect()
        }
    }

    /** CarPropertyValue timestamps are elapsedRealtimeNanos; convert to wall-clock ms. */
    private fun toEpochMs(eventElapsedNanos: Long): Long =
        System.currentTimeMillis() - (SystemClock.elapsedRealtimeNanos() - eventElapsedNanos) / 1_000_000

    private fun capability(signal: VehicleSignal, cpm: CarPropertyManager, propertyId: Int, permissionGranted: Boolean): VehicleCapability {
        if (!permissionGranted) return VehicleCapability(signal, CapabilityStatus.PERMISSION_DENIED)
        val config = runCatching { cpm.getCarPropertyConfig(propertyId) }.getOrNull()
            ?: return VehicleCapability(signal, CapabilityStatus.UNSUPPORTED)
        val rate = if (config.maxSampleRate > 0f) "${config.minSampleRate}-${config.maxSampleRate} Hz" else "on-change"
        return VehicleCapability(signal, CapabilityStatus.SUPPORTED, rate)
    }

    private fun unknownAll(reason: String) = setOf(VehicleSignal.SPEED, VehicleSignal.GEAR)
        .map { VehicleCapability(it, CapabilityStatus.UNKNOWN, reason) }
        .toSet()

    companion object {
        const val SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
    }
}
