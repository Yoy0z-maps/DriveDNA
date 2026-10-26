package dev.drivedna.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Database(
    entities = [TripEntity::class, VehicleSampleEntity::class, DrivingEventEntity::class, TripSummaryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class DriveDnaDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    /**
     * SQLite keeps deleted rows in free pages until they are reused. After a full delete (FR-012)
     * VACUUM rewrites the file so the data is gone from disk, not just hidden from queries.
     */
    suspend fun compact() = withContext(Dispatchers.IO) {
        openHelper.writableDatabase.execSQL("VACUUM")
    }

    companion object {
        const val NAME = "drivedna.db"

        /** v2 stores the explanation and quality of each summary so S05 can show them later. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trip_summary ADD COLUMN eventParamsVersion TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE trip_summary ADD COLUMN validSamples INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trip_summary ADD COLUMN totalSamples INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trip_summary ADD COLUMN qualityFlags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE trip_summary ADD COLUMN evidence TEXT NOT NULL DEFAULT ''")
            }
        }

        fun create(context: Context): DriveDnaDatabase =
            Room.databaseBuilder(context, DriveDnaDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                // A WAL file keeps old page images while other connections are open, so deleted trips
                // could survive a delete-all. The rollback journal is truncated after every transaction.
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
    }
}
