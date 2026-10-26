package dev.drivedna.core.data.db

import dev.drivedna.core.domain.model.AnalysisAvailability
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.DrivingEvent
import dev.drivedna.core.domain.model.DrivingEventType
import dev.drivedna.core.domain.model.Gear
import dev.drivedna.core.domain.model.QualityFlag
import dev.drivedna.core.domain.model.Trip
import dev.drivedna.core.domain.model.TripListItem
import dev.drivedna.core.domain.model.TripStatus
import dev.drivedna.core.domain.model.TripSummary
import dev.drivedna.core.domain.model.VehicleSample

internal fun TripEntity.toDomain() = Trip(
    id = id,
    source = DataSource.valueOf(source),
    startedAtMs = startedAtMs,
    endedAtMs = endedAtMs,
    status = TripStatus.valueOf(status),
    distanceKm = distanceKm,
    durationSec = durationSec,
    dataQuality = AnalysisAvailability.valueOf(dataQuality),
    createdAtMs = createdAtMs,
)

internal fun TripWithScore.toDomain() = TripListItem(
    trip = trip.toDomain(),
    score = score,
    availability = summaryAvailability?.let(AnalysisAvailability::valueOf),
)

internal fun VehicleSample.toEntity() = VehicleSampleEntity(
    tripId = tripId,
    timestampMs = timestampMs,
    speedKph = speedKph,
    brakePressed = brakePressed,
    gear = gear?.name,
    source = source.name,
    qualityFlags = qualityFlags.joinToString(",") { it.name },
)

internal fun VehicleSampleEntity.toDomain() = VehicleSample(
    tripId = tripId,
    timestampMs = timestampMs,
    speedKph = speedKph,
    brakePressed = brakePressed,
    gear = gear?.let(Gear::valueOf),
    source = DataSource.valueOf(source),
    qualityFlags = qualityFlags.split(',').filter { it.isNotEmpty() }.map(QualityFlag::valueOf).toSet(),
)

internal fun DrivingEvent.toEntity() = DrivingEventEntity(
    tripId = tripId,
    eventType = type.name,
    startedAtMs = startedAtMs,
    endedAtMs = endedAtMs,
    magnitude = magnitude,
    evidence = evidence,
)

internal fun DrivingEventEntity.toDomain() = DrivingEvent(
    tripId = tripId,
    type = DrivingEventType.valueOf(eventType),
    startedAtMs = startedAtMs,
    endedAtMs = endedAtMs,
    magnitude = magnitude,
    evidence = evidence,
)

internal fun TripSummary.toEntity() = TripSummaryEntity(
    tripId = tripId,
    score = score,
    scoringVersion = scoringVersion,
    eventCounts = eventCounts?.let(::encodeCounts),
    availability = availability.name,
    generatedAtMs = generatedAtMs,
    eventParamsVersion = eventParamsVersion,
    validSamples = validSamples,
    totalSamples = totalSamples,
    qualityFlags = encodeCounts(qualityFlags),
    evidence = evidence.joinToString("\n"),
)

internal fun TripSummaryEntity.toDomain() = TripSummary(
    tripId = tripId,
    score = score,
    scoringVersion = scoringVersion,
    eventParamsVersion = eventParamsVersion,
    eventCounts = eventCounts?.let { decodeCounts(it, DrivingEventType::valueOf) },
    availability = AnalysisAvailability.valueOf(availability),
    validSamples = validSamples,
    totalSamples = totalSamples,
    qualityFlags = decodeCounts(qualityFlags, QualityFlag::valueOf),
    evidence = evidence.split('\n').filter { it.isNotEmpty() },
    generatedAtMs = generatedAtMs,
)

private fun <K : Enum<K>> encodeCounts(counts: Map<K, Int>): String =
    counts.entries.sortedBy { it.key.name }.joinToString(",") { "${it.key.name}=${it.value}" }

private fun <K> decodeCounts(text: String, key: (String) -> K): Map<K, Int> =
    text.split(',').filter { it.isNotEmpty() }.associate { pair ->
        val (k, v) = pair.split('=')
        key(k) to v.toInt()
    }
