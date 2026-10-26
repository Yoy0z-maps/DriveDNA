package dev.drivedna.core.domain.recording

import dev.drivedna.core.domain.analysis.RawSpeedReading
import dev.drivedna.core.domain.analysis.SampleValidator
import dev.drivedna.core.domain.analysis.SpeedUnit
import dev.drivedna.core.domain.analysis.TripAnalyzer
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.TripStatus
import dev.drivedna.core.domain.model.VehicleSample
import dev.drivedna.core.domain.replay.ReplayScenario
import dev.drivedna.core.domain.repository.TripRepository

sealed interface StartResult {
    data class Started(val tripId: Long) : StartResult

    /** Another trip is still RECORDING or PROCESSING; no second session is opened (FR-004). */
    data class AlreadyActive(val tripId: Long) : StartResult
}

data class RecoveryReport(val recovered: List<Long>, val discarded: List<Long>)

/**
 * Trip state machine: IDLE → RECORDING → PROCESSING → COMPLETED | INTERRUPTED | FAILED.
 * Live and replay trips go through the same validation and analysis.
 */
class TripRecorder(
    private val repository: TripRepository,
    private val validator: SampleValidator = SampleValidator(),
    private val analyzer: TripAnalyzer = TripAnalyzer(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun start(source: DataSource, startedAtMs: Long = clock()): StartResult {
        repository.activeTrips().firstOrNull()?.let { return StartResult.AlreadyActive(it.id) }
        return StartResult.Started(repository.startTrip(source, startedAtMs))
    }

    suspend fun append(samples: List<VehicleSample>) {
        if (samples.isNotEmpty()) repository.appendSamples(samples)
    }

    /**
     * Analyzes and closes a trip. [readings] default to the samples already stored for the trip.
     * Any failure leaves the trip FAILED rather than stuck in PROCESSING.
     */
    suspend fun finish(
        tripId: Long,
        source: DataSource,
        status: TripStatus = TripStatus.COMPLETED,
        readings: List<RawSpeedReading>? = null,
        endedAtMs: Long = clock(),
        odometerDeltaKm: Double? = null,
    ): TripStatus {
        repository.updateStatus(tripId, TripStatus.PROCESSING)
        return try {
            val input = readings ?: repository.samplesFor(tripId).map { it.toRawReading() }
            val validation = validator.validate(input, tripId, source)
            val analysis = analyzer.analyze(validation, tripId, odometerDeltaKm)
            repository.completeTrip(tripId, endedAtMs, status, analysis, validation.samples, clock())
            status
        } catch (e: Exception) {
            repository.updateStatus(tripId, TripStatus.FAILED)
            TripStatus.FAILED
        }
    }

    /**
     * Runs a synthetic replay as a MOCK trip, rebased so it ends at [endAtMs] (default: now).
     * [variant] changes only the sensor noise seed, so a scenario can be replayed as a different day.
     */
    suspend fun recordReplay(scenario: ReplayScenario, endAtMs: Long = clock(), variant: Int = 0): StartResult {
        val readings = scenario.readings(variant)
        val offset = endAtMs - readings.last().timestampMs
        val rebased = readings.map { it.copy(timestampMs = it.timestampMs + offset) }
        val start = start(DataSource.MOCK, startedAtMs = rebased.first().timestampMs)
        if (start !is StartResult.Started) return start
        finish(start.tripId, DataSource.MOCK, readings = rebased, endedAtMs = rebased.last().timestampMs)
        return start
    }

    /**
     * Called on app/service start. Trips left open by a crash or kill are recovered as INTERRUPTED
     * when they have samples, and discarded when they have none.
     */
    suspend fun recoverInterrupted(): RecoveryReport {
        val recovered = mutableListOf<Long>()
        val discarded = mutableListOf<Long>()
        for (trip in repository.activeTrips()) {
            val samples = repository.samplesFor(trip.id)
            if (samples.isEmpty()) {
                repository.deleteTrip(trip.id)
                discarded += trip.id
            } else {
                finish(trip.id, trip.source, TripStatus.INTERRUPTED, endedAtMs = samples.last().timestampMs)
                recovered += trip.id
            }
        }
        return RecoveryReport(recovered, discarded)
    }

    private fun VehicleSample.toRawReading() =
        RawSpeedReading(timestampMs, speedKph, SpeedUnit.KILOMETERS_PER_HOUR, brakePressed)
}
