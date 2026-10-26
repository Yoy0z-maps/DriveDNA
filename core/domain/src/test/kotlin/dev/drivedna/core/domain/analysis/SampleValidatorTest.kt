package dev.drivedna.core.domain.analysis

import dev.drivedna.core.domain.model.AnalysisAvailability
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.QualityFlag
import dev.drivedna.core.domain.replay.ReplayScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleValidatorTest {
    private val validator = SampleValidator()
    private fun kph(ts: Long, v: Double?) = RawSpeedReading(ts, v, SpeedUnit.KILOMETERS_PER_HOUR)

    @Test
    fun t04_duplicateAndOutOfOrder_areFlaggedAndExcluded() {
        val base = ReplayScenario.NORMAL.readings()
        val corrupted = base.toMutableList().apply {
            add(500, base[499].copy())                                   // duplicate timestamp
            add(1000, base[900].copy(speed = 30.0))                       // late, out-of-order reading with a wild value
        }
        val result = validator.validate(corrupted, tripId = 1, source = DataSource.MOCK)
        assertEquals(1, result.summary.flagCounts[QualityFlag.DUPLICATE_TIMESTAMP])
        assertEquals(1, result.summary.flagCounts[QualityFlag.OUT_OF_ORDER])
        assertEquals(base.size, result.summary.validSpeedSamples)

        val clean = TripAnalyzer().analyze(validator.validate(base, 1, DataSource.MOCK), 1)
        val dirty = TripAnalyzer().analyze(result, 1)
        assertEquals("excluded readings must not create or remove events", clean.eventCounts, dirty.eventCounts)
        assertEquals(clean.score, dirty.score)
        assertTrue(dirty.evidence.any { it.contains("DUPLICATE_TIMESTAMP") })
    }

    @Test
    fun t05_allSpeedsMissing_isUnavailableNotZero() {
        val readings = (0 until 2_000).map { kph(it * 100L, null) }
        val analysis = TripAnalyzer().analyze(validator.validate(readings, 1, DataSource.MOCK), 1)
        assertEquals(AnalysisAvailability.UNAVAILABLE, analysis.availability)
        assertNull("unknown must not be reported as 0 events", analysis.eventCounts)
        assertNull(analysis.score)
        assertEquals(2_000, analysis.quality.flagCounts[QualityFlag.MISSING_VALUE])
    }

    @Test
    fun t05_tooFewSamples_isUnavailable() {
        val readings = (0 until 99).map { kph(it * 100L, 40.0) }
        val analysis = TripAnalyzer().analyze(validator.validate(readings, 1, DataSource.MOCK), 1)
        assertEquals(AnalysisAvailability.UNAVAILABLE, analysis.availability)
        assertNull(analysis.eventCounts)
    }

    @Test
    fun metersPerSecond_isNormalizedToKph() {
        val result = validator.validate(listOf(RawSpeedReading(0, 25.277779, SpeedUnit.METERS_PER_SECOND)), 1, DataSource.LIVE)
        assertEquals(91.0, result.samples.single().speedKph!!, 0.001)
    }

    @Test
    fun outOfRange_isExcluded() {
        val result = validator.validate(listOf(kph(0, -1.0), kph(100, 251.0), kph(200, 250.0)), 1, DataSource.MOCK)
        assertEquals(2, result.summary.flagCounts[QualityFlag.OUT_OF_RANGE])
        assertEquals(1, result.summary.validSpeedSamples)
    }

    @Test
    fun longGap_marksSegmentAndIsNotCountedAsDuration() {
        val result = validator.validate(listOf(kph(0, 10.0), kph(100, 10.0), kph(1_101, 10.0)), 1, DataSource.MOCK)
        assertEquals(setOf(QualityFlag.GAP_TOO_LONG), result.samples[2].qualityFlags)
        assertEquals(100, result.summary.validDurationMs)
        assertEquals(3, result.summary.validSpeedSamples)
    }

    @Test
    fun gapOfExactlyMax_isNotAGap() {
        val result = validator.validate(listOf(kph(0, 10.0), kph(1_000, 10.0)), 1, DataSource.MOCK)
        assertTrue(result.samples[1].qualityFlags.isEmpty())
    }
}
