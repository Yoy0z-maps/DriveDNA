package dev.drivedna.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.drivedna.app.ui.components.ScreenHeader
import dev.drivedna.core.data.prefs.UserPreferencesStore
import dev.drivedna.vehicle.aaos.AaosCarPropertySource
import kotlinx.coroutines.launch

const val CONSENT_VERSION = 1

/** S01: what is collected, where it is kept and how to delete it (FR-001). */
@Composable
fun OnboardingScreen(preferences: UserPreferencesStore, onLiveChosen: () -> Unit, onDemoChosen: () -> Unit) {
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onLiveChosen() }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader("DriveDNA 시작하기")
            Text("정차 후, 근거와 함께 내 운전 습관을 보여 드려요.", style = MaterialTheme.typography.titleLarge)
            Section("수집 목적", "급가속·급감속을 찾아 주행 점수와 개선 포인트를 계산합니다. 보험·안전 등급 평가가 아닙니다.")
            Section("수집 항목", "차량 속도, 기어 상태(P/R/N/D), 브레이크 페달 눌림 여부. 위치(GPS)는 수집하지 않습니다.")
            Section("보관", "이 차량의 앱 안에만 저장되며 외부로 전송하지 않습니다. 직접 삭제할 때까지 보관됩니다.")
            Section("삭제", "설정(S08) → 전체 데이터 삭제에서 언제든 모든 기록과 설정을 지울 수 있습니다.")
            Section(
                "권한 안내",
                "다음 단계에서 차량 속도 권한을 요청합니다. 시스템 창에는 '위치 정보에 액세스'라고 표시될 수 있지만, " +
                    "DriveDNA는 속도 값만 읽고 위치는 읽지 않습니다. 거부해도 데모 리플레이는 사용할 수 있습니다.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            preferences.setLiveConsent(granted = true, consentVersion = CONSENT_VERSION)
                            permission.launch(AaosCarPropertySource.SPEED_PERMISSION)
                        }
                    },
                    modifier = Modifier.heightIn(min = 76.dp),
                ) { Text("동의하고 Live 기록 사용", style = MaterialTheme.typography.labelLarge) }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            preferences.setLiveConsent(granted = false, consentVersion = CONSENT_VERSION)
                            onDemoChosen()
                        }
                    },
                    modifier = Modifier.heightIn(min = 76.dp),
                ) { Text("동의 없이 데모만 보기", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}
