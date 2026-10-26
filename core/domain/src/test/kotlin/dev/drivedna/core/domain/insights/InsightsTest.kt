package dev.drivedna.core.domain.insights

import dev.drivedna.core.domain.model.AnalysisAvailability
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.DrivingEventType
import dev.drivedna.core.domain.model.Trip
import dev.drivedna.core.domain.model.TripStatus
import dev.drivedna.core.domain.model.TripSummary
import dev.drivedna.core.domain.model.TripWithSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class InsightsTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val monday = ZonedDateTime.of(2026, 10, 19, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val day = 24 * 3600 * 1000L

    private fun trip(
        id: Long,
        endedAt: Long,
        score: Int?,
        accel: Int = 0,
        brake: Int = 0,
        source: DataSource = DataSource.MOCK,
        km: Double = 7.9,
        version: String = "score-v2",
        availability: AnalysisAvailability = AnalysisAvailability.AVAILABLE,
    ) = TripWithSummary(
        Trip(id, source, endedAt - 600_000, endedAt, TripStatus.COMPLETED, km, 600, availability, endedAt),
        TripSummary(
            id, score, version, "events-v1",
            if (availability == AnalysisAvailability.UNAVAILABLE) null else mapOf(DrivingEventType.HARSH_ACCELERATION to accel, DrivingEventType.HARSH_BRAKING to brake),
            availability, 100, 100, emptyMap(), emptyList(), endedAt,
        ),
    )

    @Test
    fun baseline_needsThreeEarlierTrips() {
        val trips = listOf(trip(1, monday, 86), trip(2, monday + day, 47), trip(3, monday + 2 * day, 95))
        val result = BaselineAnalyzer().compare(trips, DataSource.MOCK)
        assertTrue(result is BaselineComparison.Insufficient)
        assertEquals(2, (result as BaselineComparison.Insufficient).available)
        assertEquals(3L, result.latest!!.tripId) // coaching still knows the latest trip
    }

    @Test
    fun baseline_usesMedianOfEarlierTrips_andComparesLatest() {
        val trips = listOf(
            trip(1, monday, 86, 1, 2), trip(2, monday + day, 47, 6, 6), trip(3, monday + 2 * day, 84, 1, 2),
            trip(4, monday + 3 * day, 95, 0, 1),
        )
        val result = BaselineAnalyzer().compare(trips, DataSource.MOCK) as BaselineComparison.Available
        assertEquals(4L, result.latest.tripId)
        assertEquals(84.0, result.baseline.medianScore, 0.0)
        assertEquals(3, result.baseline.sampleSize)
        assertEquals(11.0, result.scoreDelta, 0.0)
    }

    @Test
    fun baseline_neverMixesSources() {
        val trips = listOf(
            trip(1, monday, 86), trip(2, monday + day, 80), trip(3, monday + 2 * day, 90),
            trip(4, monday + 3 * day, 50, source = DataSource.LIVE),
        )
        // LIVE has a single trip: its baseline must not borrow the three MOCK trips.
        assertTrue(BaselineAnalyzer().compare(trips, DataSource.LIVE) is BaselineComparison.Insufficient)
        assertTrue(BaselineAnalyzer().compare(trips, DataSource.MOCK) is BaselineComparison.Insufficient)
    }

    @Test
    fun baseline_neverMixesScoringVersions_andSkipsUnscoredTrips() {
        val trips = listOf(
            trip(1, monday, 86, version = "score-v1"), trip(2, monday + day, 80, version = "score-v1"),
            trip(3, monday + 2 * day, 90, version = "score-v1"), trip(4, monday + 3 * day, null, availability = AnalysisAvailability.UNAVAILABLE),
            trip(5, monday + 4 * day, 70),
        )
        val result = BaselineAnalyzer().compare(trips, DataSource.MOCK)
        assertTrue(result is BaselineComparison.Insufficient)
    }

    @Test
    fun baseline_prefersSimilarDistance_andSaysSo() {
        val trips = listOf(
            trip(1, monday, 60, km = 30.0), trip(2, monday + day, 90, km = 7.0), trip(3, monday + 2 * day, 88, km = 8.0),
            trip(4, monday + 3 * day, 86, km = 9.0), trip(5, monday + 4 * day, 95, km = 7.9),
        )
        val result = BaselineAnalyzer().compare(trips, DataSource.MOCK) as BaselineComparison.Available
        assertEquals(3, result.baseline.sampleSize)          // the 30 km trip is left out
        assertTrue(result.condition.contains("비슷한 거리"))
    }

    @Test
    fun coaching_isAtMostTwo_andBackedByNumbers() {
        val trips = listOf(
            trip(1, monday, 86, 1, 2), trip(2, monday + day, 84, 1, 2), trip(3, monday + 2 * day, 85, 1, 2),
            trip(4, monday + 3 * day, 47, 6, 6),
        )
        val comparison = BaselineAnalyzer().compare(trips, DataSource.MOCK) as BaselineComparison.Available
        val tips = CoachingRules().tips(comparison)
        assertEquals(2, tips.size)
        assertTrue(tips[0].title.contains("급한"))
        assertTrue(tips[0].evidence.contains("6회"))
        assertTrue(tips[1].title.contains("감속"))   // braking 6×4 = 24 ≥ accel 6×3 = 18
    }

    @Test
    fun coaching_noEvents_givesSingleNeutralTip_withoutBaseline() {
        val latest = TripMetrics(1, monday, 100, 0, 0, 7.9)
        val tips = CoachingRules().tips(BaselineComparison.Insufficient(DataSource.MOCK, 0, 3, "", latest))
        assertEquals(1, tips.size)
        assertTrue(tips.single().title.contains("감지되지 않았어요"))
    }

    @Test
    fun coaching_withoutAnalyzedTrip_isEmpty() {
        assertTrue(CoachingRules().tips(BaselineComparison.Insufficient(DataSource.LIVE, 0, 3, "")).isEmpty())
    }

    @Test
    fun weeklyTrend_onlyWeeksWithData_withSampleCounts() {
        val trips = listOf(
            trip(1, monday, 80, 1, 1), trip(2, monday + day, 90, 0, 1),                 // week of 10/19
            trip(3, monday + 14 * day, 60, 2, 2),                                       // week of 11/02 (10/26 week empty)
            trip(4, monday + 15 * day, null, availability = AnalysisAvailability.UNAVAILABLE),
            trip(5, monday + 15 * day, 99, source = DataSource.LIVE),
        )
        val weeks = WeeklyTrend(zone).weeks(trips, DataSource.MOCK)
        assertEquals(listOf(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 10, 19)), weeks.map { it.weekStart })
        val latest = weeks.first()
        assertEquals(2, latest.trips)
        assertEquals(1, latest.scoredTrips)
        assertEquals(60.0, latest.averageScore!!, 0.0)
        assertEquals(85.0, weeks[1].averageScore!!, 0.0)
        assertEquals(2, weeks[1].harshBraking)
    }

    @Test
    fun weeklyTrend_weekWithOnlyUnscoredTrips_hasNoAverage() {
        val weeks = WeeklyTrend(zone).weeks(listOf(trip(1, monday, null, availability = AnalysisAvailability.UNAVAILABLE)), DataSource.MOCK)
        assertNull(weeks.single().averageScore)
        assertFalse(weeks.single().scoredTrips > 0)
    }
}
