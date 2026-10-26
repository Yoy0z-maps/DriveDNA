package dev.drivedna.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.drivedna.core.domain.model.DataSource

/** Title row shared by all screens: title, optional source badge, optional back button. */
@Composable
fun ScreenHeader(title: String, source: DataSource? = null, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 76.dp)) {
                Text("← 뒤로", style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
        if (source != null) SourceBadge(source)
    }
}
