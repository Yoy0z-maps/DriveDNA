package dev.drivedna.probe

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Foreground service that only READS vehicle signals and logs them with receive timestamps:
 * AOSP PERF_VEHICLE_SPEED / GEAR_SELECTION, UX restrictions, Pleos brake pedal status.
 * No set/control calls.
 */
class ProbeService : Service() {
    private val tag = ProbeActivity.TAG
    private var car: Car? = null
    private var vehicle: Vehicle? = null
    private val lastRx = HashMap<String, Long>()

    private fun log(signal: String, msg: String) {
        val now = SystemClock.elapsedRealtime()
        val dt = lastRx.put(signal, now)?.let { now - it } ?: -1
        Log.i(tag, "RX $signal rxMs=$now dtMs=$dt $msg")
    }

    private val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val name = if (value.propertyId == VehiclePropertyIds.PERF_VEHICLE_SPEED) "speed" else "gear"
            log(name, "value=${value.value} status=${value.status} tsNs=${value.timestamp}")
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(tag, "ERR prop=0x${Integer.toHexString(propertyId)} area=$areaId")
        }
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) = log("brake", "value=$status")
        override fun onFailed(e: Exception) {
            Log.w(tag, "ERR brake ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (car == null) startAaos()
        if (vehicle == null) startPleos()
        return START_STICKY
    }

    private fun startAaos() {
        val c = Car.createCar(this)
        car = c
        val cpm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        for (id in listOf(VehiclePropertyIds.PERF_VEHICLE_SPEED, VehiclePropertyIds.GEAR_SELECTION)) {
            val config = runCatching { cpm.getCarPropertyConfig(id) }
            Log.i(tag, "CONFIG 0x${Integer.toHexString(id)} -> ${config.getOrNull() ?: config.exceptionOrNull()}")
            val ok = runCatching { cpm.registerCallback(propertyCallback, id, CarPropertyManager.SENSOR_RATE_FASTEST) }
            Log.i(tag, "REGISTER 0x${Integer.toHexString(id)} -> ${ok.getOrNull() ?: ok.exceptionOrNull()}")
        }
        val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
        Log.i(tag, "UX current=${ux.currentCarUxRestrictions}")
        ux.registerListener { r ->
            log("ux", "requiresDO=${r.isRequiresDistractionOptimization} active=0x${Integer.toHexString(r.activeRestrictions)}")
        }
    }

    private fun startPleos() {
        val v = Vehicle(this)
        vehicle = v
        v.initialize()
        // Give the vendor service a moment to bind before subscribing.
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { v.getBrake().registerBrakePedalStatus(brakeListener) }
                .onFailure { Log.w(tag, "ERR brake register $it") }
            Log.i(tag, "REGISTER brake requested")
        }, 2000)
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("probe", "Probe", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, "probe").setContentTitle("DriveDNA probe").setSmallIcon(android.R.drawable.ic_menu_info_details).build()
    }

    override fun onDestroy() {
        runCatching { vehicle?.getBrake()?.unregisterBrakePedalStatus(brakeListener) }
        vehicle?.release()
        (car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager)?.unregisterCallback(propertyCallback)
        car?.disconnect()
        super.onDestroy()
    }
}
