package dev.drivedna.core.domain.analysis

import dev.drivedna.core.domain.model.DrivingEvent
import dev.drivedna.core.domain.model.DrivingEventType
import dev.drivedna.core.domain.model.VehicleSample
import java.util.Locale
import kotlin.math.abs

/**
 * Tuning parameters for harsh acceleration / braking detection.
 * These are initial values chosen for the DriveDNA demo, NOT an official safety standard
 * (03_TECH_ARCHITECTURE §4). Any change must bump [version]; see docs/decision-log.md.
 */
data class EventParams(
    val version: String = "events-v1",
    /** Acceleration is (v_now - v_then) over the longest look-back that fits in this window. */
    val smoothingWindowMs: Long = 1_000,
    /** Look-backs shorter than this are skipped (dt ≈ 0 or too little history). */
    val minWindowMs: Long = 750,
    val harshAccelerationMps2: Double = 3.0,
    /** Negative: braking at or below this value counts. */
    val harshBrakingMps2: Double = -3.5,
    val minEventDurationMs: Long = 500,
    val minConsecutiveSamples: Int = 3,
    /** Runs of the same type closer than this are one event (quantization makes the value flicker at the threshold). */
    val mergeGapMs: Long = 1_000,
)

/** Smoothed acceleration at one sample. */
data class AccelerationPoint(val timestampMs: Long, val mps2: Double)

class EventDetector(val params: EventParams = EventParams()) {

    /** [samples] must already be validated; unusable samples are skipped and gaps split segments. */
    fun accelerations(samples: List<VehicleSample>): List<List<AccelerationPoint>> =
        segments(samples).map { segment ->
            val points = ArrayList<AccelerationPoint>()
            var j = 0
            for (i in segment.indices) {
                val ti = segment[i].timestampMs
                while (ti - segment[j].timestampMs > params.smoothingWindowMs) j++
                val dt = ti - segment[j].timestampMs
                if (dt < params.minWindowMs) continue
                val dv = (segment[i].speedKph!! - segment[j].speedKph!!) / 3.6
                points += AccelerationPoint(ti, dv / (dt / 1000.0))
            }
            points
        }

    fun detect(samples: List<VehicleSample>, tripId: Long): List<DrivingEvent> =
        accelerations(samples).flatMap { points ->
            runs(points, tripId, DrivingEventType.HARSH_ACCELERATION) { it >= params.harshAccelerationMps2 - EPSILON } +
                runs(points, tripId, DrivingEventType.HARSH_BRAKING) { it <= params.harshBrakingMps2 + EPSILON }
        }.sortedBy { it.startedAtMs }

    private fun runs(
        points: List<AccelerationPoint>,
        tripId: Long,
        type: DrivingEventType,
        matches: (Double) -> Boolean,
    ): List<DrivingEvent> {
        // 1) Contiguous runs of matching points.
        val raw = ArrayList<MutableList<AccelerationPoint>>()
        var current: MutableList<AccelerationPoint>? = null
        for (p in points) {
            if (matches(p.mps2)) {
                current = current ?: ArrayList<AccelerationPoint>().also { raw += it }
                current += p
            } else {
                current = null
            }
        }
        // 2) Merge runs separated by a short flicker.
        val merged = ArrayList<MutableList<AccelerationPoint>>()
        for (run in raw) {
            val last = merged.lastOrNull()
            if (last != null && run.first().timestampMs - last.last().timestampMs <= params.mergeGapMs) last += run else merged += run
        }
        // 3) Keep runs that are long enough.
        val threshold = if (type == DrivingEventType.HARSH_ACCELERATION) params.harshAccelerationMps2 else params.harshBrakingMps2
        return merged.mapNotNull { run ->
            val start = run.first().timestampMs
            val end = run.last().timestampMs
            if (run.size < params.minConsecutiveSamples || end - start < params.minEventDurationMs) return@mapNotNull null
            val peak = run.maxBy { abs(it.mps2) }.mps2
            DrivingEvent(
                tripId = tripId,
                type = type,
                startedAtMs = start,
                endedAtMs = end,
                magnitude = peak,
                evidence = String.format(
                    Locale.ROOT,
                    "peak %.2f m/s², %d ms, %d samples, threshold %.1f m/s² (%s)",
                    peak, end - start, run.size, threshold, params.version,
                ),
            )
        }
    }

    private fun segments(samples: List<VehicleSample>): List<List<VehicleSample>> {
        val result = ArrayList<List<VehicleSample>>()
        var current = ArrayList<VehicleSample>()
        for (s in samples) {
            if (!s.isUsableSpeed) continue
            if (s.startsNewSegment && current.isNotEmpty()) {
                result += current
                current = ArrayList()
            }
            current += s
        }
        if (current.isNotEmpty()) result += current
        return result
    }

    private companion object {
        /** Floating-point tolerance so a value exactly at the threshold counts. */
        const val EPSILON = 1e-9
    }
}
