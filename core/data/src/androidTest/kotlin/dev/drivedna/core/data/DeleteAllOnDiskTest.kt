package dev.drivedna.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.drivedna.core.data.db.DriveDnaDatabase
import dev.drivedna.core.domain.analysis.ScoringParams
import dev.drivedna.core.domain.recording.TripRecorder
import dev.drivedna.core.domain.replay.ReplayScenario
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** T08 on a real file database: after delete-all, old rows must not survive in free pages or the WAL. */
@RunWith(AndroidJUnit4::class)
class DeleteAllOnDiskTest {
    @Test
    fun deleteAll_leavesNoResidueInDatabaseFiles() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DriveDnaDatabase.NAME)
        val db = DriveDnaDatabase.create(context)
        val repo = RoomTripRepository(db.tripDao(), compact = db::compact)
        TripRecorder(repo).recordReplay(ReplayScenario.NORMAL)

        val files = listOf("", "-wal", "-journal").map { File(context.getDatabasePath(DriveDnaDatabase.NAME).path + it) }
        assertTrue("precondition: data is on disk", files.any { it.exists() && it.readBytes().containsAscii(ScoringParams.CURRENT.version) })

        repo.deleteAll()
        files.filter { it.exists() }.forEach { file ->
            assertFalse("${file.name} still contains deleted data", file.readBytes().containsAscii(ScoringParams.CURRENT.version))
            assertFalse("${file.name} still contains deleted data", file.readBytes().containsAscii("MOCK"))
        }
        db.close()
        context.deleteDatabase(DriveDnaDatabase.NAME)
    }

    private fun ByteArray.containsAscii(text: String): Boolean = String(this, Charsets.ISO_8859_1).contains(text)
}
