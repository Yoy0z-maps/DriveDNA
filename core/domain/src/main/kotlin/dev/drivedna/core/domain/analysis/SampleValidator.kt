package dev.drivedna.core.domain.analysis

import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.QualityFlag
import dev.drivedna.core.domain.model.VehicleSample

enum class SpeedUnit { METERS_PER_SECOND, KILOMETERS_PER_HOUR }

/** A reading as it arrives from a source, before unit normalization or validation. */
data class RawSpeedReading(
    val timestampMs: Long,
    val speed: Double?,
    val unit: SpeedUnit,
    val brakePressed: Boolean? = null,
)

data class ValidationParams(
    /** A gap longer than this starts a new segment; no acceleration is computed across it. */
    val maxGapMs: Long = 1_000,
    /** Physically implausible speeds for a passenger car (km/h). */
    val minSpeedKph: Double = 0.0,
    val maxSpeedKph: Double = 250.0,
)

data class QualitySummary(
    val totalReadings: Int,
    val validSpeedSamples: Int,
    /** Count per flag; a reading can carry several flags. */
    val flagCounts: Map<QualityFlag, Int>,
    /** Sum of intervals between consecutive valid samples, excluding gaps. */
    val validDurationMs: Long,
) {
    val excludedReadings: Int get() = totalReadings - validSpeedSamples
}

data class ValidationResult(
    /** Every reading in arrival order, normalized to km/h, with quality flags. */
    val samples: List<VehicleSample>,
    val summary: QualitySummary,
)

/**
 * Checks ordering, duplicates, gaps, missing values and range (FR-005).
 * Flags are attached instead of silently dropping readings, so the UI can show what was excluded.
 * A sample is usable for analysis only when it has no exclusion flag (see [VehicleSample.isUsableSpeed]).
 */
class SampleValidator(private val params: ValidationParams = ValidationParams()) {

    fun validate(readings: List<RawSpeedReading>, tripId: Long, source: DataSource): ValidationResult {
        val out = ArrayList<VehicleSample>(readings.size)
        var lastAcceptedTs: Long? = null
        var validDurationMs = 0L
        var validCount = 0

        for (r in readings) {
            val flags = mutableSetOf<QualityFlag>()
            val kph = r.speed?.let { toKph(it, r.unit) }
            val last = lastAcceptedTs

            when {
                last != null && r.timestampMs == last -> flags += QualityFlag.DUPLICATE_TIMESTAMP
                last != null && r.timestampMs < last -> flags += QualityFlag.OUT_OF_ORDER
            }
            if (kph == null) flags += QualityFlag.MISSING_VALUE
            else if (kph.isNaN() || kph < params.minSpeedKph || kph > params.maxSpeedKph) flags += QualityFlag.OUT_OF_RANGE

            val excluded = flags.any { it in EXCLUDING_FLAGS }
            if (!excluded) {
                if (last != null && r.timestampMs - last > params.maxGapMs) {
                    // Kept as a valid sample but marks a segment boundary.
                    flags += QualityFlag.GAP_TOO_LONG
                } else if (last != null) {
                    validDurationMs += r.timestampMs - last
                }
                lastAcceptedTs = r.timestampMs
                validCount++
            }
            out += VehicleSample(
                tripId = tripId,
                timestampMs = r.timestampMs,
                speedKph = kph,
                brakePressed = r.brakePressed,
                gear = null,
                source = source,
                qualityFlags = flags,
            )
        }

        val flagCounts = out.flatMap { it.qualityFlags }.groupingBy { it }.eachCount()
        return ValidationResult(out, QualitySummary(readings.size, validCount, flagCounts, validDurationMs))
    }

    private fun toKph(value: Double, unit: SpeedUnit): Double = when (unit) {
        SpeedUnit.METERS_PER_SECOND -> value * 3.6
        SpeedUnit.KILOMETERS_PER_HOUR -> value
    }

    companion object {
        val EXCLUDING_FLAGS = setOf(
            QualityFlag.DUPLICATE_TIMESTAMP,
            QualityFlag.OUT_OF_ORDER,
            QualityFlag.MISSING_VALUE,
            QualityFlag.OUT_OF_RANGE,
        )
    }
}

val VehicleSample.isUsableSpeed: Boolean
    get() = speedKph != null && qualityFlags.none { it in SampleValidator.EXCLUDING_FLAGS }

/** True when this sample follows a gap and must not be differenced with the previous one. */
val VehicleSample.startsNewSegment: Boolean
    get() = QualityFlag.GAP_TOO_LONG in qualityFlags
