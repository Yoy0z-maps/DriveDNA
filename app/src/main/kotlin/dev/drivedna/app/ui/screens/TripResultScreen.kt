package dev.drivedna.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.drivedna.app.DriveDnaApplication
import dev.drivedna.app.ui.components.ScreenHeader
import dev.drivedna.app.ui.formatDateTime
import dev.drivedna.app.ui.formatDistance
import dev.drivedna.app.ui.formatDuration
import dev.drivedna.app.ui.label
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.DrivingEventType
import dev.drivedna.core.domain.model.QualityFlag
import dev.drivedna.core.domain.model.TripDetail

/** S05: what was measured, what was found and why the score is what it is (FR-008). */
@Composable
fun TripResultScreen(app: DriveDnaApplication, tripId: Long, onBack: () -> Unit) {
    val flow = remember(tripId) { app.tripRepository.observeTripDetail(tripId) }
    val detail by flow.collectAsStateWithLifecycle(initialValue = null)
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val d = detail
            ScreenHeader("S05 주행 결과", source = d?.trip?.source, onBack = onBack)
            if (d == null) Text("불러오는 중…", style = MaterialTheme.typography.bodyLarge) else TripResultBody(d)
        }
    }
}

@Composable
private fun TripResultBody(detail: TripDetail) {
    val trip = detail.trip
    val summary = detail.summary
    if (trip.source == DataSource.MOCK) {
        Text("합성 데이터로 만든 데모 주행입니다. 실제 차량 기록이 아닙니다.", style = MaterialTheme.typography.bodyLarge)
    }
    Text(
        "${formatDateTime(trip.startedAtMs)} · ${formatDuration(trip.durationSec)} · ${formatDistance(trip.distanceKm)} · ${trip.status.label()}",
        style = MaterialTheme.typography.bodyLarge,
    )
    if (summary == null) {
        Text("아직 분석 결과가 없어요.", style = MaterialTheme.typography.titleLarge)
        return
    }
    Text(
        summary.score?.let { "점수 $it / 100" } ?: "점수 없음 — ${summary.availability.label()}",
        style = MaterialTheme.typography.headlineLarge,
    )
    Text("산식 ${summary.scoringVersion} · 이벤트 기준 ${summary.eventParamsVersion}", style = MaterialTheme.typography.bodyLarge)
    val counts = summary.eventCounts
    Text(
        if (counts == null) "급가속·급감속: 계산할 수 없음 (0회가 아님)"
        else "급가속 ${counts[DrivingEventType.HARSH_ACCELERATION] ?: 0}회 · 급감속 ${counts[DrivingEventType.HARSH_BRAKING] ?: 0}회",
        style = MaterialTheme.typography.titleLarge,
    )
    val excluded = summary.qualityFlags.filterKeys { it != QualityFlag.ODOMETER_MISMATCH }
    Text(
        "데이터 품질: ${summary.availability.label()} · 사용한 샘플 ${summary.validSamples}/${summary.totalSamples}" +
            if (excluded.isEmpty()) "" else " · 제외 사유 " + excluded.entries.joinToString(", ") { "${it.key.label()} ${it.value}개" },
        style = MaterialTheme.typography.bodyLarge,
    )
    if (QualityFlag.ODOMETER_MISMATCH in summary.qualityFlags) {
        Text("⚠ 품질 경고: 차량 주행거리계와 속도로 계산한 거리가 달라요 (점수 근거 참고)", style = MaterialTheme.typography.bodyLarge)
    }
    Text("점수 근거", style = MaterialTheme.typography.titleLarge)
    summary.evidence.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
    if (detail.events.isNotEmpty()) {
        Text("이벤트", style = MaterialTheme.typography.titleLarge)
        detail.events.forEach {
            val name = if (it.type == DrivingEventType.HARSH_ACCELERATION) "급가속" else "급감속"
            Text("• ${formatDateTime(it.startedAtMs)} $name — ${it.evidence}", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
