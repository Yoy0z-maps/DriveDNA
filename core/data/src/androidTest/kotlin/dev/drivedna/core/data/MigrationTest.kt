package dev.drivedna.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.drivedna.core.data.db.DriveDnaDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), DriveDnaDatabase::class.java)

    @Test
    fun migrate1To2_keepsExistingSummary() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO trip (id, source, startedAtMs, endedAtMs, status, distanceKm, durationSec, dataQuality, createdAtMs) " +
                    "VALUES (1, 'MOCK', 0, 1000, 'COMPLETED', 1.0, 1, 'AVAILABLE', 0)",
            )
            db.execSQL("INSERT INTO trip_summary (tripId, score, scoringVersion, eventCounts, availability, generatedAtMs) VALUES (1, 90, 'score-v1', NULL, 'AVAILABLE', 5)")
        }
        helper.runMigrationsAndValidate(DB, 2, true, DriveDnaDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT score, evidence, validSamples FROM trip_summary WHERE tripId = 1").use { c ->
                c.moveToFirst()
                assertEquals(90, c.getInt(0))
                assertEquals("", c.getString(1))
                assertEquals(0, c.getInt(2))
            }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
