package dev.drivedna.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.drivedna.app.ui.components.SourceBadge
import dev.drivedna.app.ui.formatDateTime
import dev.drivedna.app.ui.formatDistance
import dev.drivedna.app.ui.formatScore
import dev.drivedna.app.ui.label
import dev.drivedna.core.domain.model.TripListItem

/** One trip in S03/S07: when, source, status, distance and score only if it exists. */
@Composable
fun TripRow(item: TripListItem, onClick: () -> Unit) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp).heightIn(min = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatDateTime(item.trip.startedAtMs), style = MaterialTheme.typography.bodyLarge)
            SourceBadge(item.trip.source)
            Text(item.trip.status.label(), style = MaterialTheme.typography.bodyLarge)
            Text(formatDistance(item.trip.distanceKm), style = MaterialTheme.typography.bodyLarge)
            Text(formatScore(item.score), style = MaterialTheme.typography.titleLarge)
        }
    }
}
