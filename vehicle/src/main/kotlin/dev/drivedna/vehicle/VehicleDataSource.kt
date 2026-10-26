package dev.drivedna.vehicle

import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.DrivingRestriction
import dev.drivedna.core.domain.model.VehicleCapability
import dev.drivedna.core.domain.model.VehicleSample
import kotlinx.coroutines.flow.Flow

/**
 * App-internal abstraction over vehicle data (not a Pleos API). Implementations are read-only:
 * no set/control calls are allowed anywhere in this module (NFR-001).
 */
interface VehicleDataSource {
    val source: DataSource
    suspend fun capabilities(): Set<VehicleCapability>
    fun samples(tripId: Long): Flow<VehicleSample>
}

interface DrivingStateMonitor {
    val restriction: Flow<DrivingRestriction>
}
