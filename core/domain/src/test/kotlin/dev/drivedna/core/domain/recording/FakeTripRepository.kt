package dev.drivedna.core.domain.recording

import dev.drivedna.core.domain.analysis.TripAnalysis
import dev.drivedna.core.domain.model.AnalysisAvailability
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.Trip
import dev.drivedna.core.domain.model.TripDetail
import dev.drivedna.core.domain.model.TripListItem
import dev.drivedna.core.domain.model.TripStatus
import dev.drivedna.core.domain.model.TripSummary
import dev.drivedna.core.domain.model.TripWithSummary
import dev.drivedna.core.domain.model.VehicleSample
import dev.drivedna.core.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory repository mirroring the Room implementation's contract. */
class FakeTripRepository : TripRepository {
    val trips = MutableStateFlow<Map<Long, Trip>>(emptyMap())
    val samples = mutableMapOf<Long, MutableList<VehicleSample>>()
    val summaries = mutableMapOf<Long, TripSummary>()
    var failOnComplete = false
    private var nextId = 1L

    override fun observeTrips(): Flow<List<TripListItem>> =
        trips.map { all -> all.values.map { TripListItem(it, summaries[it.id]?.score, summaries[it.id]?.availability) } }

    override fun observeTripDetail(tripId: Long): Flow<TripDetail?> =
        trips.map { all -> all[tripId]?.let { TripDetail(it, summaries[tripId], emptyList()) } }

    override fun observeTripsWithSummaries(): Flow<List<TripWithSummary>> =
        trips.map { all -> all.values.map { TripWithSummary(it, summaries[it.id]) } }

    override suspend fun activeTrips() =
        trips.value.values.filter { it.status == TripStatus.RECORDING || it.status == TripStatus.PROCESSING }

    override suspend fun startTrip(source: DataSource, startedAtMs: Long): Long {
        val id = nextId++
        trips.value += id to Trip(id, source, startedAtMs, null, TripStatus.RECORDING, null, null, AnalysisAvailability.UNAVAILABLE, startedAtMs)
        return id
    }

    override suspend fun appendSamples(samples: List<VehicleSample>) {
        samples.forEach { this.samples.getOrPut(it.tripId) { mutableListOf() } += it }
    }

    override suspend fun samplesFor(tripId: Long) = samples[tripId].orEmpty().toList()

    override suspend fun updateStatus(tripId: Long, status: TripStatus) {
        trips.value += tripId to trips.value.getValue(tripId).copy(status = status)
    }

    override suspend fun completeTrip(
        tripId: Long,
        endedAtMs: Long,
        status: TripStatus,
        analysis: TripAnalysis,
        validatedSamples: List<VehicleSample>,
        generatedAtMs: Long,
    ) {
        if (failOnComplete) error("simulated storage failure")
        samples[tripId] = validatedSamples.toMutableList()
        summaries[tripId] = analysis.toSummary(generatedAtMs)
        val trip = trips.value.getValue(tripId)
        trips.value += tripId to trip.copy(
            status = status,
            endedAtMs = endedAtMs,
            distanceKm = analysis.distanceKm,
            durationSec = (endedAtMs - trip.startedAtMs) / 1000,
            dataQuality = analysis.availability,
        )
    }

    override suspend fun deleteTrip(tripId: Long) {
        trips.value -= tripId
        samples.remove(tripId)
        summaries.remove(tripId)
    }

    override suspend fun deleteAll() {
        trips.value = emptyMap()
        samples.clear()
        summaries.clear()
    }
}
