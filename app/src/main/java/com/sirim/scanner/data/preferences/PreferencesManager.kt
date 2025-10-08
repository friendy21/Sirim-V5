package com.sirim.scanner.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DATA_STORE_NAME = "user_preferences"

private val Context.dataStore by preferencesDataStore(name = DATA_STORE_NAME)

class PreferencesManager(private val context: Context) {

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        prefs.toUserPreferences()
    }

    suspend fun setStartupPage(page: StartupPage) {
        context.dataStore.edit { prefs ->
            if (page == StartupPage.AskEveryTime) {
                prefs.remove(Keys.STARTUP_PAGE)
            } else {
                prefs[Keys.STARTUP_PAGE] = page.storageKey
            }
        }
    }

    suspend fun setAuthentication(timestampMillis: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_AUTHENTICATED] = true
            prefs[Keys.AUTH_TIMESTAMP] = timestampMillis
            prefs[Keys.AUTH_EXPIRY_DURATION] = DEFAULT_AUTH_EXPIRY_MILLIS
        }
    }

    suspend fun clearAuthentication() {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_AUTHENTICATED] = false
            prefs[Keys.AUTH_TIMESTAMP] = 0L
            prefs[Keys.AUTH_EXPIRY_DURATION] = DEFAULT_AUTH_EXPIRY_MILLIS
        }
    }

    suspend fun refreshAuthentication(timestampMillis: Long) {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.IS_AUTHENTICATED] == true) {
                prefs[Keys.AUTH_TIMESTAMP] = timestampMillis
            }
        }
    }

    private fun Preferences.toUserPreferences(): UserPreferences {
        val startupPage = this[Keys.STARTUP_PAGE]?.let(StartupPage::fromStorageKey)
            ?: StartupPage.AskEveryTime
        val authenticated = this[Keys.IS_AUTHENTICATED] ?: false
        val timestamp = this[Keys.AUTH_TIMESTAMP] ?: 0L
        val expiryDuration = this[Keys.AUTH_EXPIRY_DURATION] ?: DEFAULT_AUTH_EXPIRY_MILLIS
        return UserPreferences(
            startupPage = startupPage,
            isAuthenticated = authenticated,
            authTimestamp = timestamp,
            authExpiryDurationMillis = expiryDuration
        )
    }

    private object Keys {
        val STARTUP_PAGE = stringPreferencesKey("startup_page")
        val IS_AUTHENTICATED = booleanPreferencesKey("is_authenticated")
        val AUTH_TIMESTAMP = longPreferencesKey("auth_timestamp")
        val AUTH_EXPIRY_DURATION = longPreferencesKey("auth_expiry_duration")
    }

    companion object {
        const val DEFAULT_AUTH_EXPIRY_MILLIS: Long = 60 * 60 * 1000 // 1 hour
    }
}
