package dev.drivedna.core.domain.insights

import dev.drivedna.core.domain.model.AnalysisAvailability
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.DrivingEventType
import dev.drivedna.core.domain.model.TripWithSummary

data class BaselineParams(
    /** FR-013: no baseline from fewer trips than this. */
    val minTrips: Int = 3,
    /** Most recent comparable trips used for the baseline. */
    val maxTrips: Int = 10,
    /** "Similar" distance: within this factor of the compared trip, either way. */
    val similarDistanceFactor: Double = 2.0,
)

/** One analyzed trip reduced to the numbers we compare. */
data class TripMetrics(
    val tripId: Long,
    val endedAtMs: Long,
    val score: Int,
    val harshAcceleration: Int,
    val harshBraking: Int,
    val distanceKm: Double,
)

data class BaselineStats(
    val medianScore: Double,
    val medianHarshAcceleration: Double,
    val medianHarshBraking: Double,
    val sampleSize: Int,
    val minDistanceKm: Double,
    val maxDistanceKm: Double,
)

sealed interface BaselineComparison {
    val source: DataSource

    /** Most recent scored trip of this source, if any (used for coaching even without a baseline). */
    val latest: TripMetrics?

    /** Not enough comparable trips; never a made-up comparison (FR-013). */
    data class Insufficient(
        override val source: DataSource,
        val available: Int,
        val required: Int,
        val reason: String,
        override val latest: TripMetrics? = null,
    ) : BaselineComparison

    data class Available(
        override val source: DataSource,
        override val latest: TripMetrics,
        val baseline: BaselineStats,
        val scoringVersion: String,
        /** Human-readable comparison condition shown with the result. */
        val condition: String,
    ) : BaselineComparison {
        val scoreDelta: Double get() = latest.score - baseline.medianScore
    }
}

/**
 * Compares the most recent analyzed trip with the driver's own earlier trips.
 * Only trips with the same source (MOCK vs LIVE) and the same scoring version are mixed.
 */
class BaselineAnalyzer(private val params: BaselineParams = BaselineParams()) {

    fun compare(trips: List<TripWithSummary>, source: DataSource): BaselineComparison {
        val scored = scoredTrips(trips, source)
        val latest = scored.firstOrNull()
            ?: return BaselineComparison.Insufficient(source, 0, params.minTrips, "점수가 계산된 주행이 아직 없어요")
        val version = latest.second
        val history = scored.drop(1).filter { it.second == version }.map { it.first }
        if (history.size < params.minTrips) {
            return BaselineComparison.Insufficient(
                source, history.size, params.minTrips,
                "비교할 이전 주행이 ${history.size}회예요. 같은 출처·같은 산식의 이전 주행이 ${params.minTrips}회 이상 필요해요",
                latest = latest.first,
            )
        }
        val metrics = latest.first
        val similar = history.filter {
            it.distanceKm >= metrics.distanceKm / params.similarDistanceFactor && it.distanceKm <= metrics.distanceKm * params.similarDistanceFactor
        }
        val (used, condition) = if (similar.size >= params.minTrips) {
            similar.take(params.maxTrips) to "비슷한 거리(이번 주행의 ½~2배) 주행만 비교"
        } else {
            history.take(params.maxTrips) to "비슷한 거리의 주행이 ${similar.size}회뿐이라 거리 조건 없이 비교(거리 차이가 클 수 있음)"
        }
        return BaselineComparison.Available(source, metrics, stats(used), version, condition)
    }

    /** Newest first; only trips whose summary has a score and event counts. */
    private fun scoredTrips(trips: List<TripWithSummary>, source: DataSource): List<Pair<TripMetrics, String>> =
        trips.asSequence()
            .filter { it.trip.source == source }
            .mapNotNull { t ->
                val s = t.summary ?: return@mapNotNull null
                val counts = s.eventCounts ?: return@mapNotNull null
                val score = s.score ?: return@mapNotNull null
                if (s.availability == AnalysisAvailability.UNAVAILABLE) return@mapNotNull null
                TripMetrics(
                    tripId = t.trip.id,
                    endedAtMs = t.trip.endedAtMs ?: t.trip.startedAtMs,
                    score = score,
                    harshAcceleration = counts[DrivingEventType.HARSH_ACCELERATION] ?: 0,
                    harshBraking = counts[DrivingEventType.HARSH_BRAKING] ?: 0,
                    distanceKm = t.trip.distanceKm ?: 0.0,
                ) to s.scoringVersion
            }
            .sortedWith(compareByDescending<Pair<TripMetrics, String>> { it.first.endedAtMs }.thenByDescending { it.first.tripId })
            .toList()

    private fun stats(trips: List<TripMetrics>) = BaselineStats(
        medianScore = median(trips.map { it.score.toDouble() }),
        medianHarshAcceleration = median(trips.map { it.harshAcceleration.toDouble() }),
        medianHarshBraking = median(trips.map { it.harshBraking.toDouble() }),
        sampleSize = trips.size,
        minDistanceKm = trips.minOf { it.distanceKm },
        maxDistanceKm = trips.maxOf { it.distanceKm },
    )
}

/** Median keeps one unusual trip from dragging the baseline. */
internal fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
}
