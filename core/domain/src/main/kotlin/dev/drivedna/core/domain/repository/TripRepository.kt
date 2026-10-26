package dev.drivedna.core.domain.repository

import dev.drivedna.core.domain.analysis.TripAnalysis
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.Trip
import dev.drivedna.core.domain.model.TripDetail
import dev.drivedna.core.domain.model.TripListItem
import dev.drivedna.core.domain.model.TripStatus
import dev.drivedna.core.domain.model.TripWithSummary
import dev.drivedna.core.domain.model.VehicleSample
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    fun observeTrips(): Flow<List<TripListItem>>
    fun observeTripDetail(tripId: Long): Flow<TripDetail?>
    fun observeTripsWithSummaries(): Flow<List<TripWithSummary>>

    /** Trips left in RECORDING or PROCESSING. */
    suspend fun activeTrips(): List<Trip>
    suspend fun startTrip(source: DataSource, startedAtMs: Long): Long
    suspend fun appendSamples(samples: List<VehicleSample>)
    suspend fun samplesFor(tripId: Long): List<VehicleSample>
    suspend fun updateStatus(tripId: Long, status: TripStatus)

    /** Atomically replaces the trip's samples with validated ones and stores events and summary. */
    suspend fun completeTrip(
        tripId: Long,
        endedAtMs: Long,
        status: TripStatus,
        analysis: TripAnalysis,
        validatedSamples: List<VehicleSample>,
        generatedAtMs: Long,
    )

    suspend fun deleteTrip(tripId: Long)
    suspend fun deleteAll()
}
