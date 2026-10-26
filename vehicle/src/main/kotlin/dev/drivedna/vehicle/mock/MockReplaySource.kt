package dev.drivedna.vehicle.mock

import dev.drivedna.core.domain.model.CapabilityStatus
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.VehicleCapability
import dev.drivedna.core.domain.model.VehicleSample
import dev.drivedna.core.domain.model.VehicleSignal
import dev.drivedna.vehicle.VehicleDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map

/**
 * Replays a fixed fixture. Phase 1 stub: emits the fixture as-is without timing.
 * Normal / Harsh / Improved fixtures arrive in Phase 2.
 */
class MockReplaySource(private val fixture: List<VehicleSample>) : VehicleDataSource {
    override val source = DataSource.MOCK

    override suspend fun capabilities(): Set<VehicleCapability> =
        setOf(VehicleSignal.SPEED, VehicleSignal.BRAKE_PEDAL, VehicleSignal.GEAR)
            .map { VehicleCapability(it, CapabilityStatus.SUPPORTED, "simulated") }
            .toSet()

    override fun samples(tripId: Long): Flow<VehicleSample> =
        fixture.asFlow().map { it.copy(tripId = tripId, source = DataSource.MOCK) }
}
