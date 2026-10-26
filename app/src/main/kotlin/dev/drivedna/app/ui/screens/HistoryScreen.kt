package dev.drivedna.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.drivedna.app.DriveDnaApplication
import dev.drivedna.app.ui.components.ScreenHeader

/** S07: every stored trip, newest first; survives restarts (FR-009, T07). */
@Composable
fun HistoryScreen(app: DriveDnaApplication, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val trips by app.tripRepository.observeTrips().collectAsStateWithLifecycle(initialValue = null)
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ScreenHeader("S07 기록", onBack = onBack)
            val list = trips
            when {
                list == null -> Text("불러오는 중…", style = MaterialTheme.typography.bodyLarge)
                list.isEmpty() -> Text("기록이 아직 없어요.", style = MaterialTheme.typography.titleLarge)
                else -> {
                    Text("${list.size}개 주행", style = MaterialTheme.typography.bodyLarge)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(list, key = { it.trip.id }) { item -> TripRow(item) { onOpen(item.trip.id) } }
                    }
                }
            }
        }
    }
}
