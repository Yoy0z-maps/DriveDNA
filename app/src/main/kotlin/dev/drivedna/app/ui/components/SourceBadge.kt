package dev.drivedna.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.drivedna.core.domain.model.DataSource

/** Text label for a data source. Text, not color alone, carries the meaning (04_UI_UX_SPEC §1). */
fun DataSource.badgeLabel(): String = when (this) {
    DataSource.MOCK -> "DEMO · SIMULATED"
    DataSource.LIVE -> "LIVE · VEHICLE"
}

@Composable
fun SourceBadge(source: DataSource, modifier: Modifier = Modifier) {
    val (fill, outline) = when (source) {
        DataSource.MOCK -> Color(0xFF4A3B00) to Color(0xFFFFC94D)
        DataSource.LIVE -> Color(0xFF003A1F) to Color(0xFF4DDC8C)
    }
    val label = source.badgeLabel()
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = modifier
            .semantics { contentDescription = "Data source: $label" }
            .background(fill, RoundedCornerShape(8.dp))
            .border(2.dp, outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
