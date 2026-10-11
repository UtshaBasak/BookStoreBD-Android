package io.github.utshabasak.bookstorebd.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.utshabasak.bookstorebd.core.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings that belong to the person rather than the session: the theme, and
 * the update check's memory. The language is kept by Android itself (per-app
 * language), so it is not here.
 */
@Singleton
class SettingsStore
    @Inject
    constructor(
        @SettingsPreferences private val store: DataStore<Preferences>,
    ) {
        val theme: Flow<ThemePreference> =
            store.data.map { prefs ->
                prefs[THEME]?.let { name -> ThemePreference.entries.find { it.name == name } }
                    ?: ThemePreference.System
            }

        suspend fun setTheme(theme: ThemePreference) {
            store.edit { it[THEME] = theme.name }
        }

        /**
         * A random id made the first time it is asked for, so the server can tell
         * this installation from another phone when it decides whether to send a
         * new-device e-mail. It identifies nothing else.
         */
        suspend fun installId(): String {
            store.data.first()[INSTALL_ID]?.let { return it }
            val bytes = ByteArray(INSTALL_ID_BYTES).also(SecureRandom()::nextBytes)
            val fresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            var stored = fresh
            store.edit { prefs -> stored = prefs[INSTALL_ID] ?: fresh.also { prefs[INSTALL_ID] = it } }
            return stored
        }

        /** When the update check last asked, in milliseconds. */
        suspend fun lastUpdateCheck(): Long = store.data.first()[LAST_UPDATE_CHECK] ?: 0L

        suspend fun setLastUpdateCheck(at: Long) {
            store.edit { it[LAST_UPDATE_CHECK] = at }
        }

        /** The release the person chose not to install now; it is not offered again until a newer one. */
        val dismissedUpdate: Flow<Int> = store.data.map { it[DISMISSED_UPDATE] ?: 0 }

        suspend fun dismissUpdate(versionCode: Int) {
            store.edit { it[DISMISSED_UPDATE] = versionCode }
        }

        /**
         * Whether the app keeps the person's recent searches and the books they
         * opened, on this phone only. On unless they turn it off, as the
         * website keeps them with the person's consent.
         */
        val rememberSearches: Flow<Boolean> = store.data.map { it[REMEMBER_SEARCHES] ?: true }

        val rememberViewed: Flow<Boolean> = store.data.map { it[REMEMBER_VIEWED] ?: true }

        suspend fun setRememberSearches(remember: Boolean) {
            store.edit { it[REMEMBER_SEARCHES] = remember }
        }

        suspend fun setRememberViewed(remember: Boolean) {
            store.edit { it[REMEMBER_VIEWED] = remember }
        }

        private companion object {
            val REMEMBER_SEARCHES = booleanPreferencesKey("remember_searches")
            val REMEMBER_VIEWED = booleanPreferencesKey("remember_viewed")
            val THEME = stringPreferencesKey("theme")
            val INSTALL_ID = stringPreferencesKey("install_id")
            val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
            val DISMISSED_UPDATE = intPreferencesKey("dismissed_update")
            const val INSTALL_ID_BYTES = 24
        }
    }
