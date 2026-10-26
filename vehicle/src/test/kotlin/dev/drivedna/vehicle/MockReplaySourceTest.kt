package dev.drivedna.vehicle

import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.VehicleSample
import dev.drivedna.vehicle.mock.MockReplaySource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockReplaySourceTest {
    private val fixture = listOf(
        VehicleSample(0, 1_000, 10.0, false, null, DataSource.LIVE),
        VehicleSample(0, 1_100, 12.0, false, null, DataSource.LIVE),
    )

    @Test
    fun replay_isDeterministicAndAlwaysMock() = runTest {
        val source = MockReplaySource(fixture)
        val first = source.samples(tripId = 5).toList()
        val second = source.samples(tripId = 5).toList()
        assertEquals(first, second)
        assertTrue("mock data must never be labelled LIVE", first.all { it.source == DataSource.MOCK && it.tripId == 5L })
        assertEquals(DataSource.MOCK, source.source)
    }
}
