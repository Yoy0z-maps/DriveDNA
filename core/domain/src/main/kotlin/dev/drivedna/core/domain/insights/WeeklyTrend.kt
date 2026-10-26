package dev.drivedna.core.domain.insights

import dev.drivedna.core.domain.model.AnalysisAvailability
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.DrivingEventType
import dev.drivedna.core.domain.model.TripWithSummary
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** One calendar week (Monday start) that has at least one trip (FR-014). */
data class WeekStat(
    val weekStart: LocalDate,
    val trips: Int,
    /** Trips that produced a score; the average uses only these. */
    val scoredTrips: Int,
    val averageScore: Double?,
    val harshAcceleration: Int,
    val harshBraking: Int,
    val scoringVersions: Set<String>,
)

class WeeklyTrend(private val zone: ZoneId, private val maxWeeks: Int = 8) {

    /** Newest week first. Weeks without data are omitted rather than shown as zero. */
    fun weeks(trips: List<TripWithSummary>, source: DataSource): List<WeekStat> =
        trips.filter { it.trip.source == source && it.summary != null }
            .groupBy { weekStart(it.trip.endedAtMs ?: it.trip.startedAtMs) }
            .map { (week, items) ->
                val scored = items.filter { it.summary!!.score != null && it.summary.availability != AnalysisAvailability.UNAVAILABLE }
                WeekStat(
                    weekStart = week,
                    trips = items.size,
                    scoredTrips = scored.size,
                    averageScore = scored.takeIf { it.isNotEmpty() }?.map { it.summary!!.score!! }?.average(),
                    harshAcceleration = scored.sumOf { it.summary!!.eventCounts?.get(DrivingEventType.HARSH_ACCELERATION) ?: 0 },
                    harshBraking = scored.sumOf { it.summary!!.eventCounts?.get(DrivingEventType.HARSH_BRAKING) ?: 0 },
                    scoringVersions = scored.map { it.summary!!.scoringVersion }.toSet(),
                )
            }
            .sortedByDescending { it.weekStart }
            .take(maxWeeks)

    private fun weekStart(epochMs: Long): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
