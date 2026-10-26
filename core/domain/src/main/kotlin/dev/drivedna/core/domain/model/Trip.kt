package dev.drivedna.core.domain.model

data class Trip(
    val id: Long,
    val source: DataSource,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val status: TripStatus,
    val distanceKm: Double?,
    val durationSec: Long?,
    val dataQuality: AnalysisAvailability,
    val createdAtMs: Long,
)

/**
 * One reading. Values are nullable because each signal arrives independently;
 * a missing value is never stored as 0 (FR-005, T05).
 */
data class VehicleSample(
    val tripId: Long,
    val timestampMs: Long,
    val speedKph: Double?,
    val brakePressed: Boolean?,
    val gear: Gear?,
    val source: DataSource,
    val qualityFlags: Set<QualityFlag> = emptySet(),
)

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, MANUAL, UNKNOWN }

enum class QualityFlag {
    DUPLICATE_TIMESTAMP,
    OUT_OF_ORDER,
    GAP_TOO_LONG,
    OUT_OF_RANGE,
    MISSING_VALUE,

    /** Trip-level: integrated speed distance disagrees with the vehicle odometer. */
    ODOMETER_MISMATCH,
}

enum class DrivingEventType { HARSH_ACCELERATION, HARSH_BRAKING }

data class DrivingEvent(
    val tripId: Long,
    val type: DrivingEventType,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val magnitude: Double?,
    val evidence: String,
)

data class TripSummary(
    val tripId: Long,
    /** Null when there is not enough valid data; never a fabricated score (FR-007). */
    val score: Int?,
    val scoringVersion: String,
    val eventParamsVersion: String,
    val eventCounts: Map<DrivingEventType, Int>?,
    val availability: AnalysisAvailability,
    val validSamples: Int,
    val totalSamples: Int,
    val qualityFlags: Map<QualityFlag, Int>,
    val evidence: List<String>,
    val generatedAtMs: Long,
)

/** A trip with everything S05 needs. */
data class TripDetail(val trip: Trip, val summary: TripSummary?, val events: List<DrivingEvent>)

/** A trip with its summary (if analyzed), for baselines and trends. */
data class TripWithSummary(val trip: Trip, val summary: TripSummary?)

/** A row in S07 / S03: the trip and its score if one could be computed. */
data class TripListItem(val trip: Trip, val score: Int?, val availability: AnalysisAvailability?)

data class UserPreferences(
    val consentVersion: Int?,
    val liveConsent: Boolean,
    val demoPreference: Boolean,
    val retentionDays: Int?,
)
