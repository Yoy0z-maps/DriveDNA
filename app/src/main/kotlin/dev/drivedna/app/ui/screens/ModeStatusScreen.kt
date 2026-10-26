package dev.drivedna.app.ui.screens

import android.content.pm.PackageManager
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.drivedna.app.DriveDnaApplication
import dev.drivedna.app.live.LiveRecordingService
import dev.drivedna.app.live.LiveStatus
import dev.drivedna.app.ui.components.ScreenHeader
import dev.drivedna.core.domain.model.CapabilityStatus
import dev.drivedna.core.domain.model.VehicleCapability
import dev.drivedna.core.domain.model.VehicleSignal
import dev.drivedna.vehicle.aaos.AaosCarPropertySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** S02: consent, permission and per-signal capability; Live on/off or fall back to Mock (FR-003, T06). */
@Composable
fun ModeStatusScreen(app: DriveDnaApplication, onDemo: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs by app.preferences.preferences.collectAsStateWithLifecycle(initialValue = null)
    val live by app.liveStatus.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    var capabilities by remember { mutableStateOf<List<VehicleCapability>?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val speedGranted = remember(refresh) {
        context.checkSelfPermission(AaosCarPropertySource.SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(refresh) {
        capabilities = null
        capabilities = withContext(Dispatchers.IO) {
            val aaos = runCatching { app.aaosSource.capabilities() }.getOrDefault(emptySet())
            val pleos = runCatching { app.pleosSource.capabilities() }.getOrDefault(emptySet())
            (aaos + pleos).sortedBy { it.signal.ordinal }
        }
    }

    val consent = prefs?.liveConsent == true
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader("S02 모드·연동 상태", onBack = onBack)
            Text("데이터 이용 동의: " + if (consent) "동의함" else "동의 안 함 (Live 기록 불가)", style = MaterialTheme.typography.bodyLarge)
            Text("차량 속도 권한: " + if (speedGranted) "허용됨" else "허용 안 됨", style = MaterialTheme.typography.bodyLarge)
            Text("Live 기록: ${live.label()}", style = MaterialTheme.typography.bodyLarge)

            Text("신호별 지원 상태", style = MaterialTheme.typography.titleLarge)
            val caps = capabilities
            if (caps == null) {
                Text("확인 중…", style = MaterialTheme.typography.bodyLarge)
            } else {
                caps.forEach { Text("• ${it.signal.label()}: ${it.status.label()}" + (it.detail?.let { d -> " ($d)" } ?: ""), style = MaterialTheme.typography.bodyLarge) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    !consent -> Text("Live 기록을 쓰려면 S01에서 동의가 필요해요.", style = MaterialTheme.typography.bodyLarge)
                    !speedGranted -> Button(
                        onClick = { permission.launch(AaosCarPropertySource.SPEED_PERMISSION) },
                        modifier = Modifier.heightIn(min = 76.dp),
                    ) { Text("속도 권한 다시 요청", style = MaterialTheme.typography.labelLarge) }
                    live is LiveStatus.Off || live is LiveStatus.Error -> Button(
                        onClick = { LiveRecordingService.start(context) },
                        modifier = Modifier.heightIn(min = 76.dp),
                    ) { Text("Live 기록 켜기", style = MaterialTheme.typography.labelLarge) }
                    else -> OutlinedButton(
                        onClick = { LiveRecordingService.stop(context) },
                        modifier = Modifier.heightIn(min = 76.dp),
                    ) { Text("Live 기록 끄기", style = MaterialTheme.typography.labelLarge) }
                }
                OutlinedButton(onClick = onDemo, modifier = Modifier.heightIn(min = 76.dp)) {
                    Text("데모(Mock)로 보기", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (consent && !speedGranted) {
                Text(
                    "권한이 없으면 Live 기록 대신 데모 리플레이로 기능을 확인할 수 있어요. 앱은 계속 사용할 수 있습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

private fun LiveStatus.label(): String = when (this) {
    LiveStatus.Off -> "꺼짐"
    LiveStatus.WaitingForDrive -> "켜짐 · P에서 벗어나면 기록 시작"
    is LiveStatus.Recording -> "기록 중 (주행 #$tripId) · P로 바꾸면 분석"
    is LiveStatus.Error -> "오류: $reason"
}

private fun CapabilityStatus.label(): String = when (this) {
    CapabilityStatus.SUPPORTED -> "지원(SUPPORTED)"
    CapabilityStatus.UNSUPPORTED -> "미지원(UNSUPPORTED)"
    CapabilityStatus.PERMISSION_DENIED -> "권한 없음(PERMISSION_DENIED)"
    CapabilityStatus.UNKNOWN -> "확인 불가(UNKNOWN)"
}

private fun VehicleSignal.label(): String = when (this) {
    VehicleSignal.SPEED -> "속도"
    VehicleSignal.GEAR -> "기어"
    VehicleSignal.BRAKE_PEDAL -> "브레이크 페달"
    VehicleSignal.ODOMETER -> "주행거리계"
    VehicleSignal.UX_RESTRICTION -> "주행 제한 상태"
}
