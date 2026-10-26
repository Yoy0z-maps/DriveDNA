package dev.drivedna.core.domain.analysis

import dev.drivedna.core.domain.model.AnalysisAvailability
import dev.drivedna.core.domain.model.DrivingEventType.HARSH_ACCELERATION
import dev.drivedna.core.domain.model.DrivingEventType.HARSH_BRAKING
import dev.drivedna.core.domain.replay.ReplayScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReplayScenarioTest {
    private val normal by lazy { analyzeScenario(ReplayScenario.NORMAL.run()) }
    private val harsh by lazy { analyzeScenario(ReplayScenario.HARSH.run()) }
    private val improved by lazy { analyzeScenario(ReplayScenario.IMPROVED.run()) }

    @Test
    fun t01_normal_twoRunsGiveIdenticalResults() {
        val second = analyzeScenario(ReplayScenario.NORMAL.run())
        assertEquals(normal, second)
        assertEquals(AnalysisAvailability.AVAILABLE, normal.availability)
    }

    @Test
    fun t02_harsh_hasMoreEventsAndLowerScoreThanNormal() {
        val n = normal.eventCounts!!
        val h = harsh.eventCounts!!
        assertTrue(h.getValue(HARSH_ACCELERATION) > n.getValue(HARSH_ACCELERATION))
        assertTrue(h.getValue(HARSH_BRAKING) > n.getValue(HARSH_BRAKING))
        assertTrue(harsh.score!! < normal.score!!)
    }

    @Test
    fun t03_improved_beatsNormalAndHarsh() {
        val total = { a: TripAnalysis -> a.eventCounts!!.values.sum() }
        assertTrue(total(improved) < total(normal))
        assertTrue(improved.score!! > normal.score!!)
        assertTrue(improved.score!! > harsh.score!!)
    }

    @Test
    fun scenarios_shareTheSameRoute() {
        // Same route → distances within 3 %, so the comparison reflects driving style, not route.
        val distances = listOf(normal, harsh, improved).map { it.distanceKm!! }
        assertTrue(distances.toString(), distances.max() / distances.min() < 1.03)
    }

    @Test
    fun fixtures_areMarkedSynthetic() {
        assertTrue(ReplayScenario.entries.all { it.metadata.synthetic })
    }

    @Test
    fun golden_resultsMatch() {
        val actual = listOf("NORMAL" to normal, "HARSH" to harsh, "IMPROVED" to improved)
            .joinToString("") { (name, a) -> a.goldenText(name) }
        val file = File(System.getProperty("drivedna.goldenFile"))
        if (System.getProperty("drivedna.updateGolden") == "true") {
            file.parentFile.mkdirs()
            file.writeText(actual)
        }
        assertEquals("Golden mismatch. If intended, rerun with -PupdateGolden=true and record the reason.", file.readText(), actual)
    }
}
