package dev.drivedna.app.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import dev.drivedna.app.DriveDnaApplication
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.Gear
import dev.drivedna.core.domain.model.TripStatus
import dev.drivedna.core.domain.model.VehicleSample
import dev.drivedna.core.domain.recording.StartResult
import dev.drivedna.vehicle.aaos.AaosCarPropertySource
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Collects LIVE samples in the background, because the system hides non-distraction-optimized
 * screens while driving. A trip starts when the gear leaves P and is analyzed when it returns to P.
 * Read-only: no vehicle control calls. No network.
 */
class LiveRecordingService : Service() {
    private val app get() = application as DriveDnaApplication
    private var job: Job? = null
    private val lock = Mutex()
    private var tripId: Long? = null
    /** Odometer at trip start, read in the background (Pleos SDK binds asynchronously). */
    private var odometerAtStart: Deferred<Double?>? = null
    private var lastBrake: Boolean? = null
    private val buffer = ArrayList<VehicleSample>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ServiceCompat applies the service type only on API levels that support it (minSdk 28).
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (checkSelfPermission(AaosCarPropertySource.SPEED_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            app.liveStatus.value = LiveStatus.Error("속도 권한이 없어 Live 기록을 시작할 수 없어요")
            stopSelf()
            return START_NOT_STICKY
        }
        if (job == null) job = app.appScope.launch { collect() }
        return START_NOT_STICKY
    }

    private suspend fun collect() {
        app.tripRecorder.recoverInterrupted()
        app.liveStatus.value = LiveStatus.WaitingForDrive
        val brake = app.pleosSource.samples(0).catch { Log.w(TAG, "brake unavailable", it) }
        val aaos = app.aaosSource.samples(0).catch {
            Log.w(TAG, "speed/gear unavailable", it)
            app.liveStatus.value = LiveStatus.Error("속도·기어 신호를 받을 수 없어요")
        }
        merge(aaos, brake).collect { sample -> lock.withLock { onSample(sample) } }
    }

    private suspend fun onSample(sample: VehicleSample) {
        when {
            sample.gear != null -> onGear(sample.gear!!)
            sample.speedKph != null -> tripId?.let { id ->
                buffer += sample.copy(tripId = id, brakePressed = lastBrake)
                if (buffer.size >= FLUSH_EVERY) flush()
            }
            else -> lastBrake = sample.brakePressed
        }
    }

    private suspend fun onGear(gear: Gear) {
        val active = tripId
        if (gear != Gear.PARK && active == null) {
            when (val result = app.tripRecorder.start(DataSource.LIVE)) {
                is StartResult.Started -> {
                    tripId = result.tripId
                    odometerAtStart = app.appScope.async { app.pleosSource.odometerKm() }
                    app.liveStatus.value = LiveStatus.Recording(result.tripId)
                }
                is StartResult.AlreadyActive -> Log.w(TAG, "trip ${result.tripId} already active; not starting another")
            }
        } else if (gear == Gear.PARK && active != null) {
            flush()
            tripId = null
            app.tripRecorder.finish(active, DataSource.LIVE, odometerDeltaKm = odometerDelta())
            app.liveStatus.value = LiveStatus.WaitingForDrive
        }
    }

    /** Odometer change over the trip, or null if either reading is unavailable (cross-check only). */
    private suspend fun odometerDelta(): Double? {
        val start = odometerAtStart?.await()
        odometerAtStart = null
        val end = app.pleosSource.odometerKm()
        return if (start != null && end != null) end - start else null
    }

    private suspend fun flush() {
        if (buffer.isEmpty()) return
        app.tripRecorder.append(buffer.toList())
        buffer.clear()
    }

    override fun onDestroy() {
        val running = job
        job = null
        app.appScope.launch {
            running?.cancelAndJoin()
            lock.withLock {
                // Stopped mid-trip: keep what was collected and close it as INTERRUPTED.
                tripId?.let { id ->
                    flush()
                    app.tripRecorder.finish(id, DataSource.LIVE, TripStatus.INTERRUPTED)
                }
                tripId = null
            }
            if (app.liveStatus.value !is LiveStatus.Error) app.liveStatus.value = LiveStatus.Off
        }
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "주행 기록", NotificationManager.IMPORTANCE_MIN))
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("DriveDNA 주행 기록 중")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "DriveDnaLive"
        private const val CHANNEL = "live_recording"
        private const val NOTIFICATION_ID = 1
        private const val FLUSH_EVERY = 50

        fun start(context: Context) = context.startForegroundService(Intent(context, LiveRecordingService::class.java))

        fun stop(context: Context) = context.stopService(Intent(context, LiveRecordingService::class.java))
    }
}
