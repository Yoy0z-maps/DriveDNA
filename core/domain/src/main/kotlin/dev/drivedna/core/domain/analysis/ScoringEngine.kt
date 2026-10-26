package dev.drivedna.core.domain.analysis

import dev.drivedna.core.domain.model.DrivingEventType
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Score parameters. Weights are demo tuning values, not a validated safety or insurance metric.
 *
 * - score-v1: penalties per 10 km of distance. Short trips were punished extremely
 *   (1.09 km with 3 events → 9 points), see docs/decision-log.md.
 * - score-v2: penalties per "exposure unit" = max(distance / 10 km, driving time / 20 min, 0.5).
 *   Long trips score the same as v1; short and slow trips are no longer blown up.
 */
data class ScoringParams(
    val version: String,
    val minDistanceKm: Double,
    val accelerationPenalty: Double = 3.0,
    val brakingPenalty: Double = 4.0,
    /** Null in v1: distance only. */
    val minutesPerUnit: Double? = null,
    val minExposureUnits: Double = 0.0,
) {
    companion object {
        val V1 = ScoringParams(version = "score-v1", minDistanceKm = 1.0)
        val V2 = ScoringParams(version = "score-v2", minDistanceKm = 0.5, minutesPerUnit = 20.0, minExposureUnits = 0.5)
        val CURRENT = V2
    }
}

data class ScoreResult(val score: Int?, val evidence: List<String>)

class ScoringEngine(val params: ScoringParams = ScoringParams.CURRENT) {

    /** How much driving the event counts are divided by. One unit ≈ 10 km or 20 min. */
    fun exposureUnits(distanceKm: Double, durationSec: Long): Double {
        val byDistance = distanceKm / 10.0
        val byTime = params.minutesPerUnit?.let { durationSec / 60.0 / it } ?: 0.0
        return max(max(byDistance, byTime), params.minExposureUnits)
    }

    /** Returns a null score when inputs are unavailable or the trip is too short to normalize. */
    fun score(eventCounts: Map<DrivingEventType, Int>?, distanceKm: Double?, durationSec: Long = 0): ScoreResult {
        if (eventCounts == null) return ScoreResult(null, listOf("이벤트를 계산할 수 없어 점수를 표시하지 않음"))
        if (distanceKm == null || distanceKm < params.minDistanceKm) {
            return ScoreResult(null, listOf(fmt("주행거리 %.2f km < 최소 %.1f km, 점수 미표시", distanceKm ?: 0.0, params.minDistanceKm)))
        }
        val accel = eventCounts[DrivingEventType.HARSH_ACCELERATION] ?: 0
        val brake = eventCounts[DrivingEventType.HARSH_BRAKING] ?: 0
        val units = exposureUnits(distanceKm, durationSec)
        val accelPenalty = accel / units * params.accelerationPenalty
        val brakePenalty = brake / units * params.brakingPenalty
        val score = (100.0 - accelPenalty - brakePenalty).coerceIn(0.0, 100.0).roundToInt()
        return ScoreResult(
            score,
            listOf(
                fmt("급가속 %d회 → -%.1f점", accel, accelPenalty),
                fmt("급감속 %d회 → -%.1f점", brake, brakePenalty),
                exposureEvidence(distanceKm, durationSec, units),
            ),
        )
    }

    private fun exposureEvidence(distanceKm: Double, durationSec: Long, units: Double): String =
        if (params.minutesPerUnit == null) {
            fmt("주행거리 %.2f km 기준(10 km당 감점), %s", distanceKm, params.version)
        } else {
            fmt(
                "기준량 %.2f = max(거리 %.2f km/10, 시간 %.1f분/%.0f, 최소 %.1f), %s",
                units, distanceKm, durationSec / 60.0, params.minutesPerUnit, params.minExposureUnits, params.version,
            )
        }

    private fun fmt(pattern: String, vararg args: Any) = String.format(Locale.ROOT, pattern, *args)
}
