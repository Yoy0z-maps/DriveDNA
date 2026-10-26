package dev.drivedna.core.domain.replay

import dev.drivedna.core.domain.analysis.RawSpeedReading
import dev.drivedna.core.domain.analysis.SpeedUnit
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Provenance shown with every replay. These fixtures are SYNTHETIC: generated from a scripted
 * driving profile, not recorded from a vehicle. Only the signal characteristics (sample interval,
 * jitter, 1 km/h quantization, m/s unit) were modelled on the Phase 1 emulator probe.
 */
data class FixtureMetadata(
    val id: String,
    val title: String,
    val synthetic: Boolean = true,
    val generatorVersion: String = GENERATOR_VERSION,
    val seed: Int,
    val description: String,
) {
    companion object {
        const val GENERATOR_VERSION = "replay-gen-v1"
    }
}

/**
 * One stop-to-stop leg of the shared route. Accelerations in m/s² (deceleration negative).
 * The leg length is fixed, so a harsher driver spends less time cruising, not less distance.
 */
data class Leg(
    val targetKph: Double,
    val accelerationMps2: Double,
    val distanceM: Double,
    val decelerationMps2: Double,
    val stopSec: Double,
)

enum class ReplayScenario(val metadata: FixtureMetadata, private val profile: List<Pair<Double, Double>>) {
    NORMAL(
        FixtureMetadata("normal", "Normal", seed = 1101, description = "평소 출퇴근: 대체로 부드럽고 급가속 1회·급정거 2회"),
        // (acceleration, deceleration) per leg of the shared route.
        listOf(1.8 to -2.2, 2.0 to -2.4, 3.6 to -2.0, 1.7 to -4.2, 1.9 to -2.3, 2.1 to -2.5, 1.8 to -4.4, 2.0 to -2.1),
    ),
    HARSH(
        FixtureMetadata("harsh", "Harsh", seed = 2202, description = "급한 날: 같은 경로에서 급가속·급정거 반복"),
        listOf(4.2 to -5.5, 3.8 to -4.8, 4.5 to -2.4, 2.0 to -6.0, 4.0 to -5.0, 3.9 to -4.6, 2.2 to -5.8, 4.4 to -2.6),
    ),
    IMPROVED(
        FixtureMetadata("improved", "Improved", seed = 3303, description = "코칭 후: 같은 경로, 급정거 1회만 남음"),
        listOf(1.6 to -2.0, 1.8 to -2.2, 2.0 to -1.9, 1.7 to -3.9, 1.8 to -2.1, 1.9 to -2.3, 1.7 to -2.0, 1.8 to -2.2),
    );

    /** Same route for every scenario: only the driver's acceleration and braking change. */
    val legs: List<Leg> = profile.mapIndexed { i, (acc, dec) ->
        Leg(targetKph = SharedRoute.TARGET_KPH[i], accelerationMps2 = acc, distanceM = SharedRoute.DISTANCE_M[i], decelerationMps2 = dec, stopSec = 6.0)
    }

    fun readings(variant: Int = 0): List<RawSpeedReading> = ReplayGenerator.generate(legs, metadata.seed + variant)
}

/** The stop-to-stop route every scenario drives: 8 legs, target speed and length per leg (~7.6 km). */
private object SharedRoute {
    val TARGET_KPH = listOf(50.0, 60.0, 70.0, 50.0, 60.0, 80.0, 50.0, 60.0)
    val DISTANCE_M = listOf(700.0, 1_000.0, 1_300.0, 600.0, 900.0, 1_800.0, 650.0, 950.0)
}

/**
 * Deterministic generator. Physics runs on a 10 ms grid; readings are emitted roughly every
 * 100 ms with jitter, as observed in the probe (median 100 ms, p95 ~108 ms, occasional late or
 * very short intervals), then quantized to whole km/h and reported in m/s like PERF_VEHICLE_SPEED.
 */
object ReplayGenerator {
    private const val PHYSICS_STEP_MS = 10L
    const val START_EPOCH_MS = 1_793_000_000_000L

    fun generate(legs: List<Leg>, seed: Int): List<RawSpeedReading> {
        val random = Random(seed)
        val trueSpeed = simulate(legs) // m/s per 10 ms step
        val readings = ArrayList<RawSpeedReading>()
        var t = 0L
        val endMs = (trueSpeed.size - 1) * PHYSICS_STEP_MS
        var previousMps = 0.0
        while (t <= endMs) {
            val mps = trueSpeed[(t / PHYSICS_STEP_MS).toInt()]
            val noisyKph = (mps * 3.6 + (random.nextDouble() - 0.5) * 0.6).coerceAtLeast(0.0)
            val quantizedKph = noisyKph.roundToInt()
            readings += RawSpeedReading(
                timestampMs = START_EPOCH_MS + t,
                speed = (quantizedKph / 3.6).toFloat().toDouble(),
                unit = SpeedUnit.METERS_PER_SECOND,
                brakePressed = mps < previousMps - 1e-6,
            )
            previousMps = mps
            t += nextIntervalMs(random)
        }
        return readings
    }

    private fun nextIntervalMs(random: Random): Long {
        val roll = random.nextDouble()
        return when {
            roll < 0.01 -> 13L + random.nextInt(0, 5)          // rare near-duplicate delivery
            roll < 0.04 -> 130L + random.nextInt(0, 50)        // late delivery
            else -> 100L + random.nextInt(-6, 9)               // normal jitter
        }
    }

    private fun simulate(legs: List<Leg>): DoubleArray {
        val speeds = ArrayList<Double>()
        val dt = PHYSICS_STEP_MS / 1000.0
        var v = 0.0
        fun hold(seconds: Double) = repeat((seconds / dt).toInt()) { speeds += v }
        hold(3.0)
        for (leg in legs) {
            val target = leg.targetKph / 3.6
            val accelM = target * target / (2 * leg.accelerationMps2)
            val decelM = target * target / (2 * -leg.decelerationMps2)
            require(accelM + decelM < leg.distanceM) { "leg too short for its profile" }
            while (v < target) { v = (v + leg.accelerationMps2 * dt).coerceAtMost(target); speeds += v }
            hold((leg.distanceM - accelM - decelM) / target)
            while (v > 0) { v = (v + leg.decelerationMps2 * dt).coerceAtLeast(0.0); speeds += v }
            hold(leg.stopSec)
        }
        return speeds.toDoubleArray()
    }
}
