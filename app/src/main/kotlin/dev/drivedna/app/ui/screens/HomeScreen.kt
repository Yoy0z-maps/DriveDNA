package dev.drivedna.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.drivedna.app.DriveDnaApplication
import dev.drivedna.app.navigation.Destination
import dev.drivedna.app.ui.components.ScreenHeader
import dev.drivedna.app.ui.label
import dev.drivedna.core.domain.model.DrivingRestriction

/** S03: latest result (score only when valid) and recent trips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(app: DriveDnaApplication, restriction: DrivingRestriction, onNavigate: (String) -> Unit) {
    val trips by app.tripRepository.observeTrips().collectAsStateWithLifecycle(initialValue = null)
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader("DriveDNA")
            Text("차량 상태: ${restriction.text()}", style = MaterialTheme.typography.bodyLarge)
            val list = trips
            when {
                list == null -> Text("불러오는 중…", style = MaterialTheme.typography.bodyLarge)
                list.isEmpty() -> Text("기록이 아직 없어요. 데모 리플레이로 시작해 보세요.", style = MaterialTheme.typography.titleLarge)
                else -> {
                    val latest = list.first()
                    Text("최근 주행", style = MaterialTheme.typography.titleLarge)
                    Text(
                        latest.score?.let { "점수 $it / 100" } ?: "점수 없음 — ${latest.availability?.label() ?: latest.trip.status.label()}",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    list.take(3).forEach { item -> TripRow(item) { onNavigate(Destination.tripResult(item.trip.id)) } }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(Destination.TripReplay, Destination.History, Destination.MyDna, Destination.ModeStatus, Destination.Settings).forEach { d ->
                    OutlinedButton(onClick = { onNavigate(d.route) }, modifier = Modifier.heightIn(min = 76.dp)) {
                        Text(d.title, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

private fun DrivingRestriction.text(): String = when (this) {
    DrivingRestriction.UNKNOWN -> "확인 중 (상세 화면 잠금)"
    DrivingRestriction.UNRESTRICTED -> "정차 (모든 화면 사용 가능)"
    DrivingRestriction.RESTRICTED -> "주행 중 (상세 화면 잠금)"
}
