package dev.drivedna.core.domain.analysis

import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.replay.ReplayScenario
import java.util.Locale

internal fun analyzeScenario(scenario: ScenarioRun): TripAnalysis {
    val validation = SampleValidator().validate(scenario.readings, tripId = 1, source = DataSource.MOCK)
    return TripAnalyzer().analyze(validation, tripId = 1)
}

internal data class ScenarioRun(val name: String, val readings: List<RawSpeedReading>)

internal fun ReplayScenario.run() = ScenarioRun(name, readings())

/** Stable text form used for the golden file; numbers are fixed-precision with Locale.ROOT. */
internal fun TripAnalysis.goldenText(name: String): String = buildString {
    appendLine("[$name]")
    appendLine("availability=$availability score=$score scoring=$scoringVersion events=$eventParamsVersion")
    appendLine(String.format(Locale.ROOT, "distanceKm=%.3f validDurationSec=%d", distanceKm, validDurationSec))
    appendLine("quality total=${quality.totalReadings} valid=${quality.validSpeedSamples} flags=${quality.flagCounts.toSortedMap()}")
    appendLine("eventCounts=${eventCounts?.toSortedMap()}")
    events.forEach {
        appendLine(String.format(Locale.ROOT, "  %s start=+%dms dur=%dms peak=%.2f", it.type, it.startedAtMs - 1_793_000_000_000L, (it.endedAtMs ?: it.startedAtMs) - it.startedAtMs, it.magnitude))
    }
}
