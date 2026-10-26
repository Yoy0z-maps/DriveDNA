package dev.drivedna.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.drivedna.app.BuildConfig
import dev.drivedna.app.DriveDnaApplication
import dev.drivedna.app.live.LiveRecordingService
import dev.drivedna.app.ui.components.ScreenHeader
import dev.drivedna.core.domain.analysis.EventParams
import dev.drivedna.core.domain.analysis.ScoringParams
import kotlinx.coroutines.launch

/** S08: consent, retention, delete everything (FR-012, T08), app/SDK versions. */
@Composable
fun SettingsScreen(app: DriveDnaApplication, onDeleted: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by app.preferences.preferences.collectAsStateWithLifecycle(initialValue = null)
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ScreenHeader("S08 설정", onBack = onBack)
            Text("데이터 이용 동의: " + if (prefs?.liveConsent == true) "동의함 (v${prefs?.consentVersion})" else "동의 안 함", style = MaterialTheme.typography.bodyLarge)
            Text("보관: 이 차량의 앱 안에만, 직접 삭제할 때까지. 외부 전송 없음.", style = MaterialTheme.typography.bodyLarge)
            Text(
                "앱 ${BuildConfig.VERSION_NAME} · Vehicle SDK 2.3.2 · ${ScoringParams.CURRENT.version} · ${EventParams().version}",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (prefs?.liveConsent == true) {
                OutlinedButton(
                    onClick = {
                        LiveRecordingService.stop(context)
                        scope.launch { app.preferences.setLiveConsent(granted = false, consentVersion = CONSENT_VERSION) }
                    },
                    modifier = Modifier.heightIn(min = 76.dp),
                ) { Text("Live 기록 동의 철회", style = MaterialTheme.typography.labelLarge) }
            }
            Button(
                onClick = { confirmDelete = true },
                enabled = !deleting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.heightIn(min = 76.dp),
            ) { Text(if (deleting) "삭제 중…" else "전체 데이터 삭제", style = MaterialTheme.typography.labelLarge) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("모든 데이터를 삭제할까요?") },
            text = { Text("주행 기록, 분석 결과, 동의·설정이 모두 지워지며 되돌릴 수 없어요.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(modifier = Modifier.heightIn(min = 76.dp), onClick = {
                    confirmDelete = false
                    deleting = true
                    LiveRecordingService.stop(context)
                    scope.launch {
                        app.deleteAllData()
                        onDeleted()
                    }
                }) { Text("삭제", style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = { TextButton(modifier = Modifier.heightIn(min = 76.dp), onClick = { confirmDelete = false }) { Text("취소", style = MaterialTheme.typography.labelLarge) } },
        )
    }
}
