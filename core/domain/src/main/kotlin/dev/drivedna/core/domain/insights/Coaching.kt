package dev.drivedna.core.domain.insights

import java.util.Locale

/** One suggestion with the measured numbers behind it (FR-015). */
data class CoachingTip(val title: String, val body: String, val evidence: String)

/**
 * Rule-based tips, at most two, each traceable to measured counts.
 * Wording describes what was measured; it does not claim safety, risk or fuel outcomes.
 */
class CoachingRules(private val significantScoreDelta: Double = 5.0) {

    fun tips(comparison: BaselineComparison): List<CoachingTip> {
        val latest = comparison.latest ?: return emptyList()
        val tips = mutableListOf<CoachingTip>()

        if (comparison is BaselineComparison.Available) {
            val b = comparison.baseline
            val delta = comparison.scoreDelta
            when {
                delta >= significantScoreDelta -> tips += CoachingTip(
                    "평소보다 부드러웠어요",
                    fmt("이번 점수 %d점은 내 기준선(중앙값 %.0f점)보다 %.0f점 높아요.", latest.score, b.medianScore, delta),
                    fmt("기준선: 이전 %d회, 급가속 중앙값 %.1f회·급감속 %.1f회 → 이번 %d회·%d회", b.sampleSize, b.medianHarshAcceleration, b.medianHarshBraking, latest.harshAcceleration, latest.harshBraking),
                )
                delta <= -significantScoreDelta -> tips += CoachingTip(
                    "평소보다 급한 조작이 많았어요",
                    fmt("이번 점수 %d점은 내 기준선(중앙값 %.0f점)보다 %.0f점 낮아요.", latest.score, b.medianScore, -delta),
                    fmt("기준선: 이전 %d회, 급가속 중앙값 %.1f회·급감속 %.1f회 → 이번 %d회·%d회", b.sampleSize, b.medianHarshAcceleration, b.medianHarshBraking, latest.harshAcceleration, latest.harshBraking),
                )
            }
        }

        val events = latest.harshAcceleration + latest.harshBraking
        tips += when {
            events == 0 -> CoachingTip(
                "급가속·급감속이 감지되지 않았어요",
                "이번 주행의 가감속은 모두 기준 이내였어요. 지금의 페달 조작을 유지해 보세요.",
                "급가속 0회 · 급감속 0회 (events-v1 기준)",
            )
            latest.harshBraking * 4 >= latest.harshAcceleration * 3 -> CoachingTip(
                "다음 주행: 조금 더 일찍 감속해 보세요",
                "감점의 대부분이 급감속에서 나왔어요. 앞차와의 거리를 넉넉히 두면 브레이크를 서서히 밟을 여유가 생겨요.",
                "급감속 ${latest.harshBraking}회(1회당 4점 기준) · 급가속 ${latest.harshAcceleration}회(1회당 3점 기준)",
            )
            else -> CoachingTip(
                "다음 주행: 출발할 때 가속 페달을 천천히",
                "감점의 대부분이 급가속에서 나왔어요. 출발·추월 시 페달을 2~3초에 걸쳐 밟아 보세요.",
                "급가속 ${latest.harshAcceleration}회(1회당 3점 기준) · 급감속 ${latest.harshBraking}회(1회당 4점 기준)",
            )
        }
        return tips.take(2)
    }

    private fun fmt(pattern: String, vararg args: Any) = String.format(Locale.ROOT, pattern, *args)
}
