package dev.drivedna.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.drivedna.app.navigation.DriveDnaNavHost
import dev.drivedna.app.ui.theme.DriveDnaTheme
import dev.drivedna.core.domain.model.DrivingRestriction

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DriveDnaApplication
        setContent {
            val restriction by app.drivingStateMonitor.restriction
                .collectAsStateWithLifecycle(initialValue = DrivingRestriction.UNKNOWN)
            val prefs by app.preferences.preferences.collectAsStateWithLifecycle(initialValue = null)
            DriveDnaTheme {
                prefs?.let { DriveDnaNavHost(app, restriction, consentMissing = it.consentVersion == null) }
            }
        }
    }
}
