package io.github.utshabasak.bookstorebd.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.utshabasak.bookstorebd.core.model.SessionUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in person and their refresh token, kept between launches.
 *
 * The refresh token is written only encrypted ([SecretCipher]); the access
 * token is never written at all. The file is left out of backups and device
 * transfers (res/xml/data_extraction_rules.xml), since the key that reads it
 * stays on this phone.
 */
@Singleton
class SessionStore
    @Inject
    constructor(
        @SessionPreferences private val store: DataStore<Preferences>,
        private val cipher: SecretCipher,
    ) {
        /** Who is signed in, or null. For display only; the server decides what they may do. */
        val user: Flow<SessionUser?> =
            store.data.map { prefs ->
                val id = prefs[USER_ID] ?: return@map null
                SessionUser(
                    id = id,
                    username = prefs[USERNAME].orEmpty(),
                    email = prefs[EMAIL].orEmpty(),
                    isAdmin = prefs[IS_ADMIN] ?: false,
                )
            }

        suspend fun save(
            user: SessionUser,
            refreshToken: String,
        ) {
            val sealed = cipher.encrypt(refreshToken)
            store.edit { prefs ->
                prefs[USER_ID] = user.id
                prefs[USERNAME] = user.username
                prefs[EMAIL] = user.email
                prefs[IS_ADMIN] = user.isAdmin
                prefs[REFRESH_TOKEN] = sealed
            }
        }

        /** The refresh token, decrypted; null when signed out or when it can no longer be read. */
        suspend fun refreshToken(): String? = store.data.first()[REFRESH_TOKEN]?.let(cipher::decrypt)

        suspend fun clear() {
            store.edit { it.clear() }
        }

        private companion object {
            val USER_ID = stringPreferencesKey("user_id")
            val USERNAME = stringPreferencesKey("username")
            val EMAIL = stringPreferencesKey("email")
            val IS_ADMIN = booleanPreferencesKey("is_admin")
            val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        }
    }
