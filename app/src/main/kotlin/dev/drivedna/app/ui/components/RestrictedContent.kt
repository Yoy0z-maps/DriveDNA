package dev.drivedna.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.drivedna.core.domain.model.DrivingRestriction

/**
 * Shows [content] only when detailed UI is allowed. UNKNOWN locks too (safety-first default).
 * The system already blocks non-distraction-optimized apps while driving; this is defense in depth.
 */
@Composable
fun RestrictedContent(restriction: DrivingRestriction, content: @Composable () -> Unit) {
    if (restriction.allowsDetailedUi) {
        content()
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (restriction == DrivingRestriction.RESTRICTED) "주행 중에는 상세 화면을 볼 수 없어요" else "차량 상태를 확인하는 중이에요",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "정차 후 다시 확인해 주세요.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
