package dev.drivedna.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query(
        """
        SELECT trip.*, trip_summary.score AS score, trip_summary.availability AS summaryAvailability
        FROM trip LEFT JOIN trip_summary ON trip_summary.tripId = trip.id
        ORDER BY COALESCE(trip.endedAtMs, trip.startedAtMs) DESC, trip.id DESC
        """,
    )
    fun observeTrips(): Flow<List<TripWithScore>>

    @Transaction
    @Query("SELECT * FROM trip WHERE status IN ('COMPLETED', 'INTERRUPTED') ORDER BY COALESCE(endedAtMs, startedAtMs) DESC")
    fun observeTripsWithSummaries(): Flow<List<TripWithSummaryRow>>

    @Query("SELECT * FROM trip WHERE id = :tripId")
    fun observeTrip(tripId: Long): Flow<TripEntity?>

    @Query("SELECT * FROM trip_summary WHERE tripId = :tripId")
    fun observeSummary(tripId: Long): Flow<TripSummaryEntity?>

    @Query("SELECT * FROM driving_event WHERE tripId = :tripId ORDER BY startedAtMs")
    fun observeEvents(tripId: Long): Flow<List<DrivingEventEntity>>

    @Query("SELECT * FROM trip WHERE status IN ('RECORDING', 'PROCESSING') ORDER BY startedAtMs")
    suspend fun activeTrips(): List<TripEntity>

    @Insert
    suspend fun insertTrip(trip: TripEntity): Long

    @Query("UPDATE trip SET status = :status WHERE id = :tripId")
    suspend fun updateStatus(tripId: Long, status: String)

    @Query(
        """
        UPDATE trip SET endedAtMs = :endedAtMs, status = :status, durationSec = (:endedAtMs - startedAtMs) / 1000,
        distanceKm = :distanceKm, dataQuality = :dataQuality WHERE id = :tripId
        """,
    )
    suspend fun closeTrip(tripId: Long, endedAtMs: Long, status: String, distanceKm: Double?, dataQuality: String)

    @Insert
    suspend fun insertSamples(samples: List<VehicleSampleEntity>)

    @Query("SELECT * FROM vehicle_sample WHERE tripId = :tripId ORDER BY id")
    suspend fun samplesFor(tripId: Long): List<VehicleSampleEntity>

    @Query("DELETE FROM vehicle_sample WHERE tripId = :tripId")
    suspend fun deleteSamples(tripId: Long)

    @Insert
    suspend fun insertEvents(events: List<DrivingEventEntity>)

    @Query("DELETE FROM driving_event WHERE tripId = :tripId")
    suspend fun deleteEvents(tripId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummary(summary: TripSummaryEntity)

    @Transaction
    suspend fun completeTrip(
        tripId: Long,
        endedAtMs: Long,
        status: String,
        distanceKm: Double?,
        dataQuality: String,
        samples: List<VehicleSampleEntity>,
        events: List<DrivingEventEntity>,
        summary: TripSummaryEntity,
    ) {
        deleteSamples(tripId)
        insertSamples(samples)
        deleteEvents(tripId)
        insertEvents(events)
        upsertSummary(summary)
        closeTrip(tripId, endedAtMs, status, distanceKm, dataQuality)
    }

    @Query("DELETE FROM trip WHERE id = :tripId")
    suspend fun deleteTrip(tripId: Long)

    /** Child rows are removed by ON DELETE CASCADE. */
    @Query("DELETE FROM trip")
    suspend fun deleteAllTrips()

    @Query("SELECT COUNT(*) FROM vehicle_sample")
    suspend fun countSamples(): Int
}
