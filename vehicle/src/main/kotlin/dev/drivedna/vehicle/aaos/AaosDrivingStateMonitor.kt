package dev.drivedna.vehicle.aaos

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import dev.drivedna.core.domain.model.DrivingRestriction
import dev.drivedna.vehicle.DrivingStateMonitor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Maps CarUxRestrictions to [DrivingRestriction]. Emits UNKNOWN until the car service answers. */
class AaosDrivingStateMonitor(private val context: Context) : DrivingStateMonitor {
    override val restriction: Flow<DrivingRestriction> = callbackFlow {
        trySend(DrivingRestriction.UNKNOWN)
        val car = Car.createCar(context)
        val manager = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        if (manager == null) {
            awaitClose { car?.disconnect() }
            return@callbackFlow
        }
        trySend(manager.currentCarUxRestrictions.toRestriction())
        manager.registerListener { trySend(it.toRestriction()) }
        awaitClose {
            manager.unregisterListener()
            car.disconnect()
        }
    }

    private fun CarUxRestrictions?.toRestriction(): DrivingRestriction = when {
        this == null -> DrivingRestriction.UNKNOWN
        isRequiresDistractionOptimization -> DrivingRestriction.RESTRICTED
        else -> DrivingRestriction.UNRESTRICTED
    }
}
