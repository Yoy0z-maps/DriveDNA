package dev.drivedna.core.domain.recording

import dev.drivedna.core.domain.model.AnalysisAvailability
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.TripStatus
import dev.drivedna.core.domain.model.VehicleSample
import dev.drivedna.core.domain.insights.BaselineAnalyzer
import dev.drivedna.core.domain.insights.BaselineComparison
import dev.drivedna.core.domain.insights.WeeklyTrend
import dev.drivedna.core.domain.replay.DemoHistory
import dev.drivedna.core.domain.replay.ReplayScenario
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRecorderTest {
    private val repo = FakeTripRepository()
    private var now = 1_000_000L
    private val recorder = TripRecorder(repo, clock = { now })

    private fun liveSamples(tripId: Long, seconds: Int, kph: Double = 40.0) =
        (0 until seconds * 10).map { VehicleSample(tripId, now + it * 100L, kph, false, null, DataSource.LIVE) }

    @Test
    fun start_thenFinish_goesThroughStatesToCompleted() = runTest {
        val id = (recorder.start(DataSource.LIVE) as StartResult.Started).tripId
        assertEquals(TripStatus.RECORDING, repo.trips.value.getValue(id).status)
        recorder.append(liveSamples(id, 120))
        now += 120_000
        assertEquals(TripStatus.COMPLETED, recorder.finish(id, DataSource.LIVE))
        val trip = repo.trips.value.getValue(id)
        assertEquals(TripStatus.COMPLETED, trip.status)
        assertEquals(AnalysisAvailability.AVAILABLE, trip.dataQuality)
        assertEquals(1.33, trip.distanceKm!!, 0.01)
    }

    @Test
    fun secondStart_whileRecording_isRejected() = runTest {
        val first = recorder.start(DataSource.LIVE) as StartResult.Started
        assertEquals(StartResult.AlreadyActive(first.tripId), recorder.start(DataSource.LIVE))
        assertEquals(StartResult.AlreadyActive(first.tripId), recorder.recordReplay(ReplayScenario.NORMAL))
        assertEquals(1, repo.trips.value.size)
    }

    @Test
    fun shortLiveTrip_isCompletedButUnavailable_withoutScore() = runTest {
        val id = (recorder.start(DataSource.LIVE) as StartResult.Started).tripId
        recorder.append(liveSamples(id, 5))
        recorder.finish(id, DataSource.LIVE)
        assertEquals(AnalysisAvailability.UNAVAILABLE, repo.trips.value.getValue(id).dataQuality)
        assertNull(repo.summaries.getValue(id).score)
        assertNull(repo.summaries.getValue(id).eventCounts)
    }

    @Test
    fun storageFailure_marksTripFailed_notStuckInProcessing() = runTest {
        val id = (recorder.start(DataSource.LIVE) as StartResult.Started).tripId
        repo.failOnComplete = true
        assertEquals(TripStatus.FAILED, recorder.finish(id, DataSource.LIVE))
        assertEquals(TripStatus.FAILED, repo.trips.value.getValue(id).status)
        assertTrue(repo.activeTrips().isEmpty())
    }

    @Test
    fun recovery_recoversTripsWithSamples_andDiscardsEmptyOnes() = runTest {
        val withData = (recorder.start(DataSource.LIVE) as StartResult.Started).tripId
        recorder.append(liveSamples(withData, 90))
        repo.updateStatus(withData, TripStatus.PROCESSING) // e.g. killed mid-analysis
        val empty = repo.startTrip(DataSource.LIVE, now)

        val report = recorder.recoverInterrupted()
        assertEquals(listOf(withData), report.recovered)
        assertEquals(listOf(empty), report.discarded)
        assertEquals(TripStatus.INTERRUPTED, repo.trips.value.getValue(withData).status)
        assertTrue(empty !in repo.trips.value)
        assertTrue(repo.activeTrips().isEmpty())
    }

    @Test
    fun replay_isStoredAsMock_rebasedToNow_withGoldenScore() = runTest {
        val id = (recorder.recordReplay(ReplayScenario.HARSH) as StartResult.Started).tripId
        val trip = repo.trips.value.getValue(id)
        assertEquals(DataSource.MOCK, trip.source)
        assertEquals(TripStatus.COMPLETED, trip.status)
        assertEquals(now, trip.endedAtMs)
        assertEquals(546L, trip.durationSec) // replay length, not 0
        assertEquals(47, repo.summaries.getValue(id).score)
        assertTrue(repo.samplesFor(id).all { it.source == DataSource.MOCK })
    }

    @Test
    fun demoHistory_thenImproved_givesBaselineAndPositiveComparison() = runTest {
        DemoHistory.record(recorder, now)
        recorder.recordReplay(ReplayScenario.IMPROVED)
        val trips = repo.observeTripsWithSummaries().first()
        assertEquals(5, trips.size)
        assertTrue(trips.all { it.trip.source == DataSource.MOCK })
        val comparison = BaselineAnalyzer().compare(trips, DataSource.MOCK) as BaselineComparison.Available
        assertEquals(4, comparison.baseline.sampleSize)
        assertTrue("Improved should beat the demo baseline", comparison.scoreDelta >= 5)
        val weeks = WeeklyTrend(ZoneId.of("Asia/Seoul")).weeks(trips, DataSource.MOCK)
        assertTrue("two weeks of history span at least two calendar weeks", weeks.size >= 2)
        assertEquals(5, weeks.sumOf { it.trips })
    }
}
