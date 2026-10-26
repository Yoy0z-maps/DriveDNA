package dev.drivedna.vehicle.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import android.content.Context
import dev.drivedna.core.domain.model.CapabilityStatus
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.VehicleCapability
import dev.drivedna.core.domain.model.VehicleSample
import dev.drivedna.core.domain.model.VehicleSignal
import dev.drivedna.vehicle.VehicleDataSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Pleos Vehicle SDK 2.3.2 reads (brake pedal status). Read-only: only get/register/check APIs
 * are used, never set* or CONTROL_* APIs.
 */
class PleosVehicleSdkSource(private val context: Context) : VehicleDataSource {
    override val source = DataSource.LIVE

    override suspend fun capabilities(): Set<VehicleCapability> {
        val vehicle = Vehicle(context).also { it.initialize() }
        return try {
            // The SDK binds to the vendor service asynchronously; retry briefly before reporting UNKNOWN.
            var result = checkBrake(vehicle)
            var retries = 0
            while (result.status == CapabilityStatus.UNKNOWN && retries++ < CAPABILITY_RETRIES) {
                delay(CAPABILITY_RETRY_DELAY_MS)
                result = checkBrake(vehicle)
            }
            setOf(result)
        } catch (e: Exception) {
            setOf(VehicleCapability(VehicleSignal.BRAKE_PEDAL, CapabilityStatus.UNKNOWN, e.javaClass.simpleName))
        } finally {
            vehicle.release()
        }
    }

    /**
     * One-shot read of the vehicle odometer (Pleos `getCurrentDistanceDriven`, km), used only to
     * cross-check the distance integrated from speed. Null when the SDK cannot answer.
     */
    suspend fun odometerKm(): Double? {
        val vehicle = Vehicle(context).also { it.initialize() }
        return try {
            delay(CAPABILITY_RETRY_DELAY_MS) // let the SDK bind
            withTimeoutOrNull(ODOMETER_TIMEOUT_MS) {
                suspendCoroutine { cont ->
                    vehicle.getOdometer().getCurrentDistanceDriven(
                        onComplete = { cont.resume(it?.toDouble()) },
                        onFailed = { cont.resume(null) },
                    )
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            vehicle.release()
        }
    }

    private suspend fun checkBrake(vehicle: Vehicle): VehicleCapability = suspendCoroutine { cont ->
        vehicle.getBrake().checkBrakePedalStatusCapabilities(
            onComplete = { cont.resume(VehicleCapability(VehicleSignal.BRAKE_PEDAL, CapabilityStatus.SUPPORTED)) },
            onFailed = { cont.resume(VehicleCapability(VehicleSignal.BRAKE_PEDAL, CapabilityStatus.UNKNOWN, it.message)) },
        )
    }

    override fun samples(tripId: Long): Flow<VehicleSample> = callbackFlow {
        val vehicle = Vehicle(context).also { it.initialize() }
        val listener = object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(status: Boolean?) {
                trySend(VehicleSample(tripId, System.currentTimeMillis(), null, status, null, DataSource.LIVE))
            }

            override fun onFailed(e: Exception) = Unit
        }
        delay(CAPABILITY_RETRY_DELAY_MS) // let the SDK bind before subscribing
        vehicle.getBrake().registerBrakePedalStatus(listener)
        awaitClose {
            vehicle.getBrake().unregisterBrakePedalStatus(listener)
            vehicle.release()
        }
    }

    private companion object {
        const val CAPABILITY_RETRIES = 4
        const val CAPABILITY_RETRY_DELAY_MS = 700L
        const val ODOMETER_TIMEOUT_MS = 3_000L
    }
}
