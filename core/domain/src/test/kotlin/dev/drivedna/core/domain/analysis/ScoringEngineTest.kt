package dev.drivedna.core.domain.analysis

import dev.drivedna.core.domain.model.DrivingEventType.HARSH_ACCELERATION
import dev.drivedna.core.domain.model.DrivingEventType.HARSH_BRAKING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringEngineTest {
    private val v1 = ScoringEngine(ScoringParams.V1)
    private val v2 = ScoringEngine(ScoringParams.V2)
    private fun counts(a: Int, b: Int) = mapOf(HARSH_ACCELERATION to a, HARSH_BRAKING to b)

    @Test
    fun noEvents_is100() {
        assertEquals(100, v1.score(counts(0, 0), 5.0).score)
        assertEquals(100, v2.score(counts(0, 0), 5.0, 600).score)
    }

    @Test
    fun v1_penaltiesAreNormalizedPer10Km() {
        assertEquals(93, v1.score(counts(1, 1), 10.0).score)
        assertEquals(86, v1.score(counts(1, 1), 5.0).score)
    }

    @Test
    fun scoreIsClampedAtZero() {
        assertEquals(0, v1.score(counts(50, 50), 1.0).score)
        assertEquals(0, v2.score(counts(50, 50), 1.0, 60).score)
    }

    @Test
    fun v1_shortTrip_hasNoScore() {
        assertNull(v1.score(counts(0, 0), 0.99).score)
        assertEquals(100, v1.score(counts(0, 0), 1.0).score)
    }

    @Test
    fun unavailableEvents_haveNoScore() {
        assertNull(v1.score(null, 10.0).score)
        assertNull(v2.score(null, 10.0, 600).score)
    }

    @Test
    fun v2_minimumDistanceIsHalfAKilometre() {
        assertNull(v2.score(counts(0, 0), 0.49, 300).score)
        assertEquals(100, v2.score(counts(0, 0), 0.5, 300).score)
    }

    @Test
    fun v2_exposure_usesLargestOfDistanceTimeAndFloor() {
        assertEquals(1.0, v2.exposureUnits(10.0, 600), 1e-9)      // distance dominates
        assertEquals(1.5, v2.exposureUnits(3.0, 30 * 60), 1e-9)   // slow traffic: 30 min → 1.5 units
        assertEquals(0.5, v2.exposureUnits(1.09, 77), 1e-9)       // short trip → floor
        assertEquals(0.109, v1.exposureUnits(1.09, 77), 1e-9)     // v1 had no floor
    }

    @Test
    fun v2_matchesV1_onLongTrips() {
        // 7.9 km / 9.5 min: distance dominates, so v1 and v2 agree (replay golden scores unchanged).
        for ((a, b) in listOf(1 to 2, 6 to 6, 0 to 1)) {
            assertEquals(v1.score(counts(a, b), 7.9).score, v2.score(counts(a, b), 7.9, 569).score)
        }
    }

    @Test
    fun v2_shortLiveTrip_isNoLongerExtreme() {
        // Phase 3 live trip: 1.09 km, 77 s, 2 harsh acceleration + 1 harsh braking → v1 gave 9.
        assertEquals(9, v1.score(counts(2, 1), 1.095, 77).score) // shown as "1.09 km"
        assertEquals(80, v2.score(counts(2, 1), 1.095, 77).score)
        // Phase 3 live trip 2: 0.75 km had no v1 score at all.
        assertNull(v1.score(counts(2, 2), 0.75, 68).score)
        assertEquals(72, v2.score(counts(2, 2), 0.75, 68).score)
    }

    @Test
    fun v2_stillPenalizesMoreEventsMore() {
        val few = v2.score(counts(1, 0), 1.0, 120).score!!
        val many = v2.score(counts(4, 4), 1.0, 120).score!!
        assertTrue(many < few)
    }

    @Test
    fun evidence_namesVersionAndExposure() {
        val e = v2.score(counts(1, 1), 1.09, 77).evidence.joinToString()
        assertTrue(e, e.contains("score-v2") && e.contains("기준량 0.50"))
    }
}
