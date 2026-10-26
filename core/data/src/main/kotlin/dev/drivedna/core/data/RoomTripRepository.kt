package dev.drivedna.core.data

import android.content.Context
import dev.drivedna.core.data.db.DriveDnaDatabase
import dev.drivedna.core.data.db.TripDao
import dev.drivedna.core.data.db.TripEntity
import dev.drivedna.core.data.db.toDomain
import dev.drivedna.core.data.db.toEntity
import dev.drivedna.core.domain.analysis.TripAnalysis
import dev.drivedna.core.domain.model.AnalysisAvailability
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.TripDetail
import dev.drivedna.core.domain.model.TripListItem
import dev.drivedna.core.domain.model.TripStatus
import dev.drivedna.core.domain.model.TripWithSummary
import dev.drivedna.core.domain.model.VehicleSample
import dev.drivedna.core.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomTripRepository(
    private val dao: TripDao,
    /** Physically removes deleted rows from the file (free pages, WAL); see [DriveDnaDatabase.compact]. */
    private val compact: suspend () -> Unit = {},
) : TripRepository {
    override fun observeTrips(): Flow<List<TripListItem>> = dao.observeTrips().map { rows -> rows.map { it.toDomain() } }

    override fun observeTripDetail(tripId: Long): Flow<TripDetail?> =
        combine(dao.observeTrip(tripId), dao.observeSummary(tripId), dao.observeEvents(tripId)) { trip, summary, events ->
            trip?.let { TripDetail(it.toDomain(), summary?.toDomain(), events.map { e -> e.toDomain() }) }
        }

    override fun observeTripsWithSummaries(): Flow<List<TripWithSummary>> =
        dao.observeTripsWithSummaries().map { rows -> rows.map { TripWithSummary(it.trip.toDomain(), it.summary?.toDomain()) } }

    override suspend fun activeTrips() = dao.activeTrips().map { it.toDomain() }

    override suspend fun startTrip(source: DataSource, startedAtMs: Long): Long = dao.insertTrip(
        TripEntity(
            source = source.name,
            startedAtMs = startedAtMs,
            endedAtMs = null,
            status = TripStatus.RECORDING.name,
            distanceKm = null,
            durationSec = null,
            dataQuality = AnalysisAvailability.UNAVAILABLE.name,
            createdAtMs = startedAtMs,
        ),
    )

    override suspend fun appendSamples(samples: List<VehicleSample>) = dao.insertSamples(samples.map { it.toEntity() })

    override suspend fun samplesFor(tripId: Long) = dao.samplesFor(tripId).map { it.toDomain() }

    override suspend fun updateStatus(tripId: Long, status: TripStatus) = dao.updateStatus(tripId, status.name)

    override suspend fun completeTrip(
        tripId: Long,
        endedAtMs: Long,
        status: TripStatus,
        analysis: TripAnalysis,
        validatedSamples: List<VehicleSample>,
        generatedAtMs: Long,
    ) = dao.completeTrip(
        tripId = tripId,
        endedAtMs = endedAtMs,
        status = status.name,
        distanceKm = analysis.distanceKm,
        dataQuality = analysis.availability.name,
        samples = validatedSamples.map { it.toEntity() },
        events = analysis.events.map { it.toEntity() },
        summary = analysis.toSummary(generatedAtMs).toEntity(),
    )

    override suspend fun deleteTrip(tripId: Long) = dao.deleteTrip(tripId)

    override suspend fun deleteAll() {
        dao.deleteAllTrips()
        compact()
    }
}

/** Entry point for the app module so Room stays an implementation detail of core:data. */
fun createTripRepository(context: Context): TripRepository {
    val db = DriveDnaDatabase.create(context)
    return RoomTripRepository(db.tripDao(), compact = db::compact)
}
