package dev.drivedna.core.domain.analysis

import dev.drivedna.core.domain.model.AnalysisAvailability
import dev.drivedna.core.domain.model.DrivingEvent
import dev.drivedna.core.domain.model.DrivingEventType
import dev.drivedna.core.domain.model.QualityFlag
import dev.drivedna.core.domain.model.TripSummary
import dev.drivedna.core.domain.model.VehicleSample
import java.util.Locale
import kotlin.math.abs

/** Integrated speed distance vs the vehicle odometer over the same trip. */
data class DistanceCheck(val integratedKm: Double, val odometerKm: Double, val toleranceKm: Double) {
    val mismatch: Boolean get() = abs(integratedKm - odometerKm) > toleranceKm
}

data class AnalysisParams(
    /** Below these, events are UNAVAILABLE rather than "0 events" (FR-006, T05). */
    val minValidSamples: Int = 100,
    val minValidDurationMs: Long = 60_000,
    /** More than this share of excluded readings, or any gap, makes the result PARTIAL. */
    val partialExcludedRatio: Double = 0.05,
    /** Odometer cross-check tolerance: the larger of an absolute and a relative bound. */
    val odometerToleranceKm: Double = 0.5,
    val odometerToleranceRatio: Double = 0.15,
)

data class TripAnalysis(
    val tripId: Long,
    val availability: AnalysisAvailability,
    val quality: QualitySummary,
    val distanceKm: Double?,
    val validDurationSec: Long,
    val events: List<DrivingEvent>,
    /** Null when events could not be computed; never an empty map standing in for "unknown". */
    val eventCounts: Map<DrivingEventType, Int>?,
    val score: Int?,
    val scoringVersion: String,
    val eventParamsVersion: String,
    val evidence: List<String>,
    val distanceCheck: DistanceCheck? = null,
) {
    fun toSummary(generatedAtMs: Long) = TripSummary(
        tripId = tripId,
        score = score,
        scoringVersion = scoringVersion,
        eventParamsVersion = eventParamsVersion,
        eventCounts = eventCounts,
        availability = availability,
        validSamples = quality.validSpeedSamples,
        totalSamples = quality.totalReadings,
        qualityFlags = if (distanceCheck?.mismatch == true) quality.flagCounts + (QualityFlag.ODOMETER_MISMATCH to 1) else quality.flagCounts,
        evidence = evidence,
        generatedAtMs = generatedAtMs,
    )
}

/** Pure, deterministic analysis: same validated input → same output (NFR-005). */
class TripAnalyzer(
    private val detector: EventDetector = EventDetector(),
    private val scoring: ScoringEngine = ScoringEngine(),
    private val params: AnalysisParams = AnalysisParams(),
) {
    /** [odometerDeltaKm] is the vehicle odometer change over the trip, when it could be read. */
    fun analyze(validation: ValidationResult, tripId: Long, odometerDeltaKm: Double? = null): TripAnalysis {
        val quality = validation.summary
        val samples = validation.samples
        val enoughData = quality.validSpeedSamples >= params.minValidSamples &&
            quality.validDurationMs >= params.minValidDurationMs

        if (!enoughData) {
            return TripAnalysis(
                tripId = tripId,
                availability = AnalysisAvailability.UNAVAILABLE,
                quality = quality,
                distanceKm = null,
                validDurationSec = quality.validDurationMs / 1000,
                events = emptyList(),
                eventCounts = null,
                score = null,
                scoringVersion = scoring.params.version,
                eventParamsVersion = detector.params.version,
                evidence = listOf(
                    "유효 속도 샘플 ${quality.validSpeedSamples}개 / ${quality.validDurationMs / 1000}초: " +
                        "분석에 필요한 ${params.minValidSamples}개·${params.minValidDurationMs / 1000}초 미만",
                ),
            )
        }

        val events = detector.detect(samples, tripId)
        val counts = DrivingEventType.entries.associateWith { type -> events.count { it.type == type } }
        val distanceKm = distanceKm(samples)
        val scoreResult = scoring.score(counts, distanceKm, quality.validDurationMs / 1000)
        val distanceCheck = odometerDeltaKm?.let {
            DistanceCheck(distanceKm, it, maxOf(params.odometerToleranceKm, params.odometerToleranceRatio * maxOf(distanceKm, it)))
        }
        val excludedRatio = quality.excludedReadings.toDouble() / quality.totalReadings
        val hasGap = (quality.flagCounts[QualityFlag.GAP_TOO_LONG] ?: 0) > 0
        val availability = if (excludedRatio > params.partialExcludedRatio || hasGap) AnalysisAvailability.PARTIAL else AnalysisAvailability.AVAILABLE

        return TripAnalysis(
            tripId = tripId,
            availability = availability,
            quality = quality,
            distanceKm = distanceKm,
            validDurationSec = quality.validDurationMs / 1000,
            events = events,
            eventCounts = counts,
            score = scoreResult.score,
            scoringVersion = scoring.params.version,
            eventParamsVersion = detector.params.version,
            evidence = scoreResult.evidence + exclusionEvidence(quality) + listOfNotNull(distanceCheck?.let(::distanceEvidence)),
            distanceCheck = distanceCheck,
        )
    }

    private fun distanceEvidence(check: DistanceCheck): String = String.format(
        Locale.ROOT,
        "주행거리계 %.2f km vs 속도 적분 %.2f km — %s(허용 ±%.2f km)",
        check.odometerKm, check.integratedKm, if (check.mismatch) "불일치" else "일치", check.toleranceKm,
    )

    private fun exclusionEvidence(quality: QualitySummary): String =
        if (quality.flagCounts.isEmpty()) {
            "제외된 샘플 없음 (${quality.totalReadings}개 모두 사용)"
        } else {
            "제외된 샘플 ${quality.excludedReadings}/${quality.totalReadings}: " +
                quality.flagCounts.entries.sortedBy { it.key.name }.joinToString(", ") { "${it.key}=${it.value}" }
        }

    /** Trapezoidal integration over consecutive usable samples, not across gaps. */
    private fun distanceKm(samples: List<VehicleSample>): Double {
        var meters = 0.0
        var prev: VehicleSample? = null
        for (s in samples) {
            if (!s.isUsableSpeed) continue
            val p = prev
            if (p != null && !s.startsNewSegment) {
                val dtSec = (s.timestampMs - p.timestampMs) / 1000.0
                meters += (p.speedKph!! + s.speedKph!!) / 2.0 / 3.6 * dtSec
            }
            prev = s
        }
        return meters / 1000.0
    }
}
