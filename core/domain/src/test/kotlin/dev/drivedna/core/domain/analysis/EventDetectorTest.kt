package dev.drivedna.core.domain.analysis

import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.DrivingEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDetectorTest {
    private val detector = EventDetector()

    /** Constant acceleration [a] (m/s²) sampled every [stepMs], exact (unquantized) speeds in km/h. */
    private fun ramp(a: Double, durationMs: Long, startKph: Double = 50.0, stepMs: Long = 100): List<RawSpeedReading> =
        (0..durationMs / stepMs).map { i ->
            val t = i * stepMs
            RawSpeedReading(t, (startKph + a * 3.6 * t / 1000.0).coerceAtLeast(0.0), SpeedUnit.KILOMETERS_PER_HOUR)
        }

    private fun detect(readings: List<RawSpeedReading>) =
        detector.detect(SampleValidator().validate(readings, 1, DataSource.MOCK).samples, 1)

    @Test
    fun accelerationExactlyAtThreshold_counts() {
        val events = detect(ramp(3.0, 2_000))
        assertEquals(1, events.size)
        assertEquals(DrivingEventType.HARSH_ACCELERATION, events.single().type)
    }

    @Test
    fun accelerationJustBelowThreshold_doesNotCount() {
        assertTrue(detect(ramp(2.95, 2_000)).isEmpty())
    }

    @Test
    fun brakingAtThreshold_counts_andBelowDoesNot() {
        assertEquals(DrivingEventType.HARSH_BRAKING, detect(ramp(-3.5, 2_000)).single().type)
        assertTrue(detect(ramp(-3.45, 2_000)).isEmpty())
    }

    @Test
    fun spikeShorterThanMinDuration_isIgnored() {
        // Flat, then one 1-sample jump of +5 km/h (a quantization-like glitch), then flat again.
        val readings = (0..40).map { i -> RawSpeedReading(i * 100L, if (i == 20) 55.0 else 50.0, SpeedUnit.KILOMETERS_PER_HOUR) }
        assertTrue(detect(readings).isEmpty())
    }

    @Test
    fun noAccelerationAcrossAGap() {
        // 50 → 90 km/h across a 2 s gap would be 5.6 m/s² if differenced; it must not be.
        val before = (0..20).map { RawSpeedReading(it * 100L, 50.0, SpeedUnit.KILOMETERS_PER_HOUR) }
        val after = (0..20).map { RawSpeedReading(4_000 + it * 100L, 90.0, SpeedUnit.KILOMETERS_PER_HOUR) }
        assertTrue(detect(before + after).isEmpty())
    }

    @Test
    fun nearZeroIntervals_areNotDifferenced() {
        // Two readings 13 ms apart with a 2 km/h step would be ~43 m/s² if differenced directly.
        val readings = (0..30).flatMap { i ->
            listOf(RawSpeedReading(i * 100L, 50.0, SpeedUnit.KILOMETERS_PER_HOUR), RawSpeedReading(i * 100L + 13, if (i == 15) 52.0 else 50.0, SpeedUnit.KILOMETERS_PER_HOUR))
        }
        assertTrue(detect(readings).isEmpty())
    }

    @Test
    fun evidence_namesThresholdAndVersion() {
        val e = detect(ramp(4.0, 2_000)).single()
        assertTrue(e.evidence, e.evidence.contains("threshold 3.0") && e.evidence.contains("events-v1"))
    }
}
