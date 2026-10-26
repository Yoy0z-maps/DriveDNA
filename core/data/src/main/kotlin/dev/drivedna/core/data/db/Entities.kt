package dev.drivedna.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "trip")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val status: String,
    val distanceKm: Double?,
    val durationSec: Long?,
    val dataQuality: String,
    val createdAtMs: Long,
)

@Entity(
    tableName = "vehicle_sample",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class VehicleSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val timestampMs: Long,
    val speedKph: Double?,
    val brakePressed: Boolean?,
    val gear: String?,
    val source: String,
    /** Comma-separated [dev.drivedna.core.domain.model.QualityFlag] names. */
    val qualityFlags: String,
)

@Entity(
    tableName = "driving_event",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class DrivingEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val eventType: String,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val magnitude: Double?,
    val evidence: String,
)

@Entity(
    tableName = "trip_summary",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
)
data class TripSummaryEntity(
    @PrimaryKey val tripId: Long,
    val score: Int?,
    val scoringVersion: String,
    /** "TYPE=count" pairs, comma-separated; null when events could not be computed. */
    val eventCounts: String?,
    val availability: String,
    val generatedAtMs: Long,
    // v2
    @ColumnInfo(defaultValue = "") val eventParamsVersion: String,
    @ColumnInfo(defaultValue = "0") val validSamples: Int,
    @ColumnInfo(defaultValue = "0") val totalSamples: Int,
    /** "FLAG=count" pairs, comma-separated. */
    @ColumnInfo(defaultValue = "") val qualityFlags: String,
    /** One evidence line per row, newline-separated. */
    @ColumnInfo(defaultValue = "") val evidence: String,
)

/** Trip joined with its summary's score for list screens. */
data class TripWithScore(
    @Embedded val trip: TripEntity,
    val score: Int?,
    val summaryAvailability: String?,
)

/** Trip with its summary for baselines and weekly trends. */
data class TripWithSummaryRow(
    @Embedded val trip: TripEntity,
    @Relation(parentColumn = "id", entityColumn = "tripId") val summary: TripSummaryEntity?,
)
