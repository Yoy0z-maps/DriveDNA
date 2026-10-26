package dev.drivedna.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.drivedna.core.data.db.DriveDnaDatabase
import dev.drivedna.core.domain.analysis.ScoringParams
import dev.drivedna.core.domain.model.AnalysisAvailability
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.DrivingEventType
import dev.drivedna.core.domain.model.TripStatus
import dev.drivedna.core.domain.recording.StartResult
import dev.drivedna.core.domain.recording.TripRecorder
import dev.drivedna.core.domain.replay.ReplayScenario
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTripRepositoryTest {
    private lateinit var db: DriveDnaDatabase
    private lateinit var repo: RoomTripRepository
    private lateinit var recorder: TripRecorder

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DriveDnaDatabase::class.java).build()
        repo = RoomTripRepository(db.tripDao(), compact = db::compact)
        recorder = TripRecorder(repo)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun replay_isPersistedWithSummaryEventsAndSamples() = runTest {
        val id = (recorder.recordReplay(ReplayScenario.NORMAL) as StartResult.Started).tripId
        val detail = repo.observeTripDetail(id).first()!!
        assertEquals(TripStatus.COMPLETED, detail.trip.status)
        assertEquals(DataSource.MOCK, detail.trip.source)
        assertEquals(86, detail.summary!!.score)
        assertEquals(mapOf(DrivingEventType.HARSH_ACCELERATION to 1, DrivingEventType.HARSH_BRAKING to 2), detail.summary!!.eventCounts)
        assertEquals(3, detail.events.size)
        assertTrue(detail.summary!!.evidence.any { it.contains(ScoringParams.CURRENT.version) })
        assertEquals(5_605, repo.samplesFor(id).size)

        val list = repo.observeTrips().first()
        assertEquals(86, list.single().score)
    }

    @Test
    fun unavailableTrip_keepsNullCountsAndScore() = runTest {
        val id = (recorder.start(DataSource.LIVE) as StartResult.Started).tripId
        recorder.finish(id, DataSource.LIVE)
        val summary = repo.observeTripDetail(id).first()!!.summary!!
        assertEquals(AnalysisAvailability.UNAVAILABLE, summary.availability)
        assertNull(summary.eventCounts)
        assertNull(summary.score)
    }

    @Test
    fun deleteAll_cascadesToEveryTable() = runTest {
        recorder.recordReplay(ReplayScenario.HARSH)
        recorder.recordReplay(ReplayScenario.IMPROVED)
        repo.deleteAll()
        assertTrue(repo.observeTrips().first().isEmpty())
        assertEquals(0, db.tripDao().countSamples())
        val cursor = db.openHelper.readableDatabase.query("SELECT (SELECT COUNT(*) FROM driving_event) + (SELECT COUNT(*) FROM trip_summary)")
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
        cursor.close()
    }

    @Test
    fun activeTrip_blocksSecondSession_andIsRecoveredAfterRestart() = runTest {
        val id = (recorder.start(DataSource.LIVE) as StartResult.Started).tripId
        assertEquals(StartResult.AlreadyActive(id), recorder.start(DataSource.LIVE))
        // Simulate a process death: a new recorder over the same database.
        val report = TripRecorder(repo).recoverInterrupted()
        assertEquals(listOf(id), report.discarded)
        assertTrue(repo.activeTrips().isEmpty())
    }

    @Test
    fun tripsWithSummaries_excludeOpenTrips_andCarrySummaries() = runTest {
        recorder.recordReplay(ReplayScenario.NORMAL)
        recorder.recordReplay(ReplayScenario.HARSH)
        recorder.start(DataSource.LIVE) // still RECORDING: not part of baselines
        val rows = repo.observeTripsWithSummaries().first()
        assertEquals(2, rows.size)
        assertEquals(listOf(47, 86), rows.map { it.summary!!.score })
        assertTrue(rows.all { it.summary!!.scoringVersion == ScoringParams.CURRENT.version })
    }
}
