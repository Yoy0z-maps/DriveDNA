package dev.drivedna.app

import android.app.Application
import dev.drivedna.app.live.LiveStatus
import dev.drivedna.core.data.createTripRepository
import dev.drivedna.core.data.prefs.UserPreferencesStore
import dev.drivedna.core.domain.recording.TripRecorder
import dev.drivedna.core.domain.repository.TripRepository
import dev.drivedna.vehicle.DrivingStateMonitor
import dev.drivedna.vehicle.aaos.AaosCarPropertySource
import dev.drivedna.vehicle.aaos.AaosDrivingStateMonitor
import dev.drivedna.vehicle.pleos.PleosVehicleSdkSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Manual DI container; small enough that a framework is not justified yet. */
class DriveDnaApplication : Application() {
    /** Outlives screens and the live service, e.g. to finish a trip after the service stops. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val tripRepository: TripRepository by lazy { createTripRepository(this) }
    val tripRecorder: TripRecorder by lazy { TripRecorder(tripRepository) }
    val preferences: UserPreferencesStore by lazy { UserPreferencesStore(this) }
    val drivingStateMonitor: DrivingStateMonitor by lazy { AaosDrivingStateMonitor(this) }
    val aaosSource: AaosCarPropertySource by lazy { AaosCarPropertySource(this) }
    val pleosSource: PleosVehicleSdkSource by lazy { PleosVehicleSdkSource(this) }
    val liveStatus = MutableStateFlow<LiveStatus>(LiveStatus.Off)

    override fun onCreate() {
        super.onCreate()
        // Trips left RECORDING/PROCESSING by a killed process are recovered or discarded (FR-004).
        appScope.launch { tripRecorder.recoverInterrupted() }
    }

    /** FR-012: removes trips, preferences and cached files. */
    suspend fun deleteAllData() {
        tripRepository.deleteAll()
        preferences.clear()
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }
}
