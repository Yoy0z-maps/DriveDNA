package dev.drivedna.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.drivedna.app.ui.components.ScreenHeader
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.recording.StartResult
import dev.drivedna.core.domain.recording.TripRecorder
import dev.drivedna.core.domain.replay.DemoHistory
import dev.drivedna.core.domain.replay.ReplayScenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** S04: run a synthetic replay; the result is stored as a MOCK trip and opened in S05. */
@Composable
fun TripReplayScreen(recorder: TripRecorder, onResult: (Long) -> Unit, onBack: () -> Unit) {
    var running by remember { mutableStateOf<ReplayScenario?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var buildingHistory by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ScreenHeader("S04 주행 리플레이", source = DataSource.MOCK, onBack = onBack)
            Text("합성 데이터로 만든 데모 주행입니다. 실제 차량 기록이 아닙니다.", style = MaterialTheme.typography.bodyLarge)
            ReplayScenario.entries.forEach { scenario ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = running == null && !buildingHistory,
                        onClick = {
                            running = scenario
                            message = null
                            scope.launch {
                                val result = withContext(Dispatchers.Default) { recorder.recordReplay(scenario) }
                                running = null
                                when (result) {
                                    is StartResult.Started -> onResult(result.tripId)
                                    is StartResult.AlreadyActive -> message = "다른 주행(#${result.tripId})이 기록 중이라 리플레이를 시작하지 않았어요."
                                }
                            }
                        },
                        modifier = Modifier.heightIn(min = 76.dp),
                    ) { Text(scenario.metadata.title, style = MaterialTheme.typography.labelLarge) }
                    Text(scenario.metadata.description, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text("데모 순서: ① 데모 이력 만들기 → ② Improved 실행 → ③ My DNA에서 기준선 비교", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(
                enabled = running == null && !buildingHistory,
                onClick = {
                    buildingHistory = true
                    message = null
                    scope.launch {
                        val results = withContext(Dispatchers.Default) { DemoHistory.record(recorder, System.currentTimeMillis()) }
                        buildingHistory = false
                        val made = results.count { it is StartResult.Started }
                        message = if (made == DemoHistory.ENTRIES.size) {
                            "지난 2주 데모 이력 ${made}회(합성 데이터)를 만들었어요. 이제 Improved를 실행해 보세요."
                        } else {
                            "다른 주행이 기록 중이라 데모 이력을 ${made}회만 만들었어요."
                        }
                    }
                },
                modifier = Modifier.heightIn(min = 76.dp),
            ) { Text(if (buildingHistory) "데모 이력 만드는 중…" else "① 데모 이력 만들기 (지난 2주, ${DemoHistory.ENTRIES.size}회)", style = MaterialTheme.typography.labelLarge) }
            running?.let { Text("${it.metadata.title} 분석 중…", style = MaterialTheme.typography.bodyLarge) }
            message?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
