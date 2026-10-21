package io.github.utshabasak.bookstorebd.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

        /**
         * The highest version code any update check has seen, so a phone that
         * starts offline still knows an update is waiting. Only ever rises.
         */
        val newestVersionSeen: Flow<Int> = store.data.map { it[NEWEST_VERSION_SEEN] ?: 0 }

        suspend fun sawVersion(versionCode: Int) {
            store.edit { prefs -> if (versionCode > (prefs[NEWEST_VERSION_SEEN] ?: 0)) prefs[NEWEST_VERSION_SEEN] = versionCode }
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

        /** How the wishlist is ordered, by name; null until the person picks one. */
        val wishlistSort: Flow<String?> = store.data.map { it[WISHLIST_SORT] }

        suspend fun setWishlistSort(name: String) {
            store.edit { it[WISHLIST_SORT] = name }
        }

        /** The Account tab's side, buyer or seller, as the website keeps it on the device. */
        val profileMode: Flow<String?> = store.data.map { it[PROFILE_MODE] }

        suspend fun setProfileMode(name: String) {
            store.edit { it[PROFILE_MODE] = name }
        }

        /** The language voice search listens in, chosen in the search panel ("en-US" or "bn-BD"). */
        val voiceLanguage: Flow<String?> = store.data.map { it[VOICE_LANGUAGE] }

        suspend fun setVoiceLanguage(tag: String) {
            store.edit { it[VOICE_LANGUAGE] = tag }
        }

        /**
         * This phone's push token, as Firebase last gave it: filed with the
         * server again on each start, so the server knows the phone is still
         * there. Removed on signing out.
         */
        suspend fun pushToken(): String? = store.data.first()[PUSH_TOKEN]

        suspend fun setPushToken(token: String?) {
            store.edit { if (token == null) it.remove(PUSH_TOKEN) else it[PUSH_TOKEN] = token }
        }

        private companion object {
            val PUSH_TOKEN = stringPreferencesKey("push_token")
            val REMEMBER_SEARCHES = booleanPreferencesKey("remember_searches")
            val REMEMBER_VIEWED = booleanPreferencesKey("remember_viewed")
            val THEME = stringPreferencesKey("theme")
            val WISHLIST_SORT = stringPreferencesKey("wishlist_sort")
            val PROFILE_MODE = stringPreferencesKey("profile_mode")
            val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
            val INSTALL_ID = stringPreferencesKey("install_id")
            val NEWEST_VERSION_SEEN = intPreferencesKey("newest_version_seen")
            const val INSTALL_ID_BYTES = 24
        }
    }
