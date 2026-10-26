package dev.drivedna.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.drivedna.core.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

class UserPreferencesStore(private val context: Context) {
    val preferences: Flow<UserPreferences> = context.dataStore.data.map { p ->
        UserPreferences(
            consentVersion = p[CONSENT_VERSION],
            liveConsent = p[LIVE_CONSENT] ?: false,
            demoPreference = p[DEMO_PREFERENCE] ?: true,
            retentionDays = p[RETENTION_DAYS],
        )
    }

    suspend fun setLiveConsent(granted: Boolean, consentVersion: Int) {
        context.dataStore.edit {
            it[LIVE_CONSENT] = granted
            it[CONSENT_VERSION] = consentVersion
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private companion object {
        val CONSENT_VERSION = intPreferencesKey("consent_version")
        val LIVE_CONSENT = booleanPreferencesKey("live_consent")
        val DEMO_PREFERENCE = booleanPreferencesKey("demo_preference")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
    }
}
