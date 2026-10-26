package dev.drivedna.core.domain.replay

import dev.drivedna.core.domain.analysis.SampleValidator
import dev.drivedna.core.domain.analysis.TripAnalysis
import dev.drivedna.core.domain.analysis.TripAnalyzer
import dev.drivedna.core.domain.model.DataSource

/** Validates and analyzes a synthetic replay. Always tagged MOCK. */
class RunReplayUseCase(
    private val validator: SampleValidator = SampleValidator(),
    private val analyzer: TripAnalyzer = TripAnalyzer(),
) {
    operator fun invoke(scenario: ReplayScenario, tripId: Long = 0): TripAnalysis {
        val validation = validator.validate(scenario.readings(), tripId, DataSource.MOCK)
        return analyzer.analyze(validation, tripId)
    }
}
