package dev.drivedna.core.domain.analysis

import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.QualityFlag
import dev.drivedna.core.domain.replay.ReplayScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceCheckTest {
    private val validation = SampleValidator().validate(ReplayScenario.NORMAL.readings(), 1, DataSource.MOCK)

    @Test
    fun matchingOdometer_isNotFlagged() {
        val a = TripAnalyzer().analyze(validation, 1, odometerDeltaKm = 8.0)
        assertFalse(a.distanceCheck!!.mismatch)
        assertNull(a.toSummary(0).qualityFlags[QualityFlag.ODOMETER_MISMATCH])
        assertTrue(a.evidence.last().contains("일치"))
    }

    @Test
    fun disagreeingOdometer_isFlagged_butScoreKept() {
        val a = TripAnalyzer().analyze(validation, 1, odometerDeltaKm = 0.0)
        assertTrue(a.distanceCheck!!.mismatch)
        assertEquals(1, a.toSummary(0).qualityFlags[QualityFlag.ODOMETER_MISMATCH])
        assertEquals(86, a.score)
    }

    @Test
    fun toleranceIsLargerOfAbsoluteAndRelative() {
        assertFalse(DistanceCheck(1.0, 1.5, 0.5).mismatch)
        assertTrue(DistanceCheck(1.0, 1.51, 0.5).mismatch)
    }

    @Test
    fun noOdometer_noCheck() {
        assertNull(TripAnalyzer().analyze(validation, 1).distanceCheck)
    }
}
