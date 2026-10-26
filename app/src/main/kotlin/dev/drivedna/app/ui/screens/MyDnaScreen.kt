package dev.drivedna.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.drivedna.app.DriveDnaApplication
import dev.drivedna.app.ui.components.ScreenHeader
import dev.drivedna.app.ui.components.badgeLabel
import dev.drivedna.core.domain.insights.BaselineAnalyzer
import dev.drivedna.core.domain.insights.BaselineComparison
import dev.drivedna.core.domain.insights.CoachingRules
import dev.drivedna.core.domain.insights.CoachingTip
import dev.drivedna.core.domain.insights.WeekStat
import dev.drivedna.core.domain.insights.WeeklyTrend
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.TripWithSummary
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** S06: latest trip vs my own baseline, up to two tips, weekly trend (FR-013/014/015). */
@Composable
fun MyDnaScreen(app: DriveDnaApplication, onBack: () -> Unit) {
    val trips by app.tripRepository.observeTripsWithSummaries().collectAsStateWithLifecycle(initialValue = null)
    var chosen by remember { mutableStateOf<DataSource?>(null) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val all = trips
            // Default to the source of the most recent trip; MOCK and LIVE are never mixed.
            val source = chosen ?: all?.firstOrNull()?.trip?.source ?: DataSource.MOCK
            ScreenHeader("S06 My DNA", source = source, onBack = onBack)
            Text("운전 습관 디지털 프로필 — 나의 이전 주행과 비교합니다. 안전·보험 평가가 아닙니다.", style = MaterialTheme.typography.bodyLarge)
            SourceSelector(source) { chosen = it }
            if (all == null) {
                Text("불러오는 중…", style = MaterialTheme.typography.bodyLarge)
            } else {
                InsightsBody(all, source)
            }
        }
    }
}

@Composable
private fun SourceSelector(selected: DataSource, onSelect: (DataSource) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        DataSource.entries.forEach { s ->
            val label = (if (s == selected) "✓ " else "") + s.badgeLabel()
            val modifier = Modifier.heightIn(min = 76.dp).semantics {
                role = Role.Tab
                this.selected = s == selected
            }
            if (s == selected) {
                Button(onClick = { onSelect(s) }, modifier = modifier) { Text(label, style = MaterialTheme.typography.labelLarge) }
            } else {
                OutlinedButton(onClick = { onSelect(s) }, modifier = modifier) { Text(label, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun InsightsBody(trips: List<TripWithSummary>, source: DataSource) {
    val comparison = remember(trips, source) { BaselineAnalyzer().compare(trips, source) }
    val tips = remember(comparison) { CoachingRules().tips(comparison) }
    val weeks = remember(trips, source) { WeeklyTrend(ZoneId.systemDefault()).weeks(trips, source) }

    Text("최근 주행 vs 내 기준선", style = MaterialTheme.typography.titleLarge)
    when (comparison) {
        is BaselineComparison.Insufficient -> {
            Text("비교 데이터 부족 (${comparison.available}/${comparison.required}회)", style = MaterialTheme.typography.headlineLarge)
            Text(comparison.reason, style = MaterialTheme.typography.bodyLarge)
        }
        is BaselineComparison.Available -> ComparisonBlock(comparison)
    }

    Text("다음 주행을 위한 제안", style = MaterialTheme.typography.titleLarge)
    if (tips.isEmpty()) {
        Text("점수가 계산된 주행이 생기면 제안을 보여 드려요.", style = MaterialTheme.typography.bodyLarge)
    } else {
        tips.forEach { TipBlock(it) }
    }

    Text("주간 추세", style = MaterialTheme.typography.titleLarge)
    if (weeks.isEmpty()) {
        Text("추세를 보여 줄 기록이 아직 없어요.", style = MaterialTheme.typography.bodyLarge)
    } else {
        Text("기록이 있는 주만 표시합니다(월요일 시작).", style = MaterialTheme.typography.bodyLarge)
        weeks.forEachIndexed { i, week -> WeekRow(week, previous = weeks.getOrNull(i + 1)) }
    }
}

@Composable
private fun ComparisonBlock(c: BaselineComparison.Available) {
    val b = c.baseline
    val delta = c.scoreDelta
    val direction = when {
        delta > 0.5 -> "▲ ${fmt("%.0f", delta)}점 높음"
        delta < -0.5 -> "▼ ${fmt("%.0f", -delta)}점 낮음"
        else -> "= 비슷함"
    }
    Text("이번 ${c.latest.score}점 · 기준선 ${fmt("%.0f", b.medianScore)}점 ($direction)", style = MaterialTheme.typography.headlineLarge)
    Text(
        "급가속 이번 ${c.latest.harshAcceleration}회 / 기준선 ${fmt("%.1f", b.medianHarshAcceleration)}회 · " +
            "급감속 이번 ${c.latest.harshBraking}회 / 기준선 ${fmt("%.1f", b.medianHarshBraking)}회",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        "비교 조건: ${c.source.badgeLabel()} · ${c.scoringVersion} · 이전 ${b.sampleSize}회(중앙값, " +
            "${fmt("%.1f", b.minDistanceKm)}–${fmt("%.1f", b.maxDistanceKm)} km) · ${c.condition}",
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun TipBlock(tip: CoachingTip) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tip.title, style = MaterialTheme.typography.titleLarge)
            Text(tip.body, style = MaterialTheme.typography.bodyLarge)
            Text("근거: ${tip.evidence}", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun WeekRow(week: WeekStat, previous: WeekStat?) {
    val avg = week.averageScore
    val change = if (avg != null && previous?.averageScore != null) {
        val d = avg - previous.averageScore!!
        when {
            abs(d) < 0.5 -> " (지난 기록 주와 비슷)"
            d > 0 -> " (▲ ${fmt("%.0f", d)}점)"
            else -> " (▼ ${fmt("%.0f", -d)}점)"
        }
    } else {
        ""
    }
    Text(
        "${week.weekStart.format(WEEK_FORMAT)} 주 · 주행 ${week.trips}회(점수 ${week.scoredTrips}회) · " +
            (avg?.let { "평균 ${fmt("%.0f", it)}점$change" } ?: "점수 없음") +
            " · 급가속 ${week.harshAcceleration} · 급감속 ${week.harshBraking}" +
            if (week.scoringVersions.size > 1) " · 산식 혼재 ${week.scoringVersions}" else "",
        style = MaterialTheme.typography.bodyLarge,
    )
}

private val WEEK_FORMAT = DateTimeFormatter.ofPattern("M/d", Locale.KOREA)

private fun fmt(pattern: String, vararg args: Any) = String.format(Locale.ROOT, pattern, *args)
