package dev.drivedna.core.domain.replay

import dev.drivedna.core.domain.recording.StartResult
import dev.drivedna.core.domain.recording.TripRecorder

/**
 * A synthetic two-week history so the demo can show a personal baseline and weekly trend
 * (FR-013/014) before the final Improved replay. Every trip is a MOCK replay; only the noise seed
 * and the end time differ.
 */
object DemoHistory {
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    data class Entry(val scenario: ReplayScenario, val daysAgo: Int, val variant: Int)

    val ENTRIES = listOf(
        Entry(ReplayScenario.NORMAL, daysAgo = 13, variant = 1),
        Entry(ReplayScenario.NORMAL, daysAgo = 9, variant = 2),
        Entry(ReplayScenario.HARSH, daysAgo = 6, variant = 1),
        Entry(ReplayScenario.NORMAL, daysAgo = 2, variant = 3),
    )

    /** Records [ENTRIES] ending at 08:30-ish relative to [nowMs]; stops at the first refused start. */
    suspend fun record(recorder: TripRecorder, nowMs: Long): List<StartResult> {
        val results = mutableListOf<StartResult>()
        for (entry in ENTRIES) {
            val result = recorder.recordReplay(entry.scenario, endAtMs = nowMs - entry.daysAgo * DAY_MS, variant = entry.variant)
            results += result
            if (result !is StartResult.Started) break
        }
        return results
    }
}
