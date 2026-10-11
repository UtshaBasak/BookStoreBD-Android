package io.github.utshabasak.bookstorebd.core.data.session

import io.github.utshabasak.bookstorebd.core.common.BookStoreDispatchers
import io.github.utshabasak.bookstorebd.core.common.Dispatcher
import io.github.utshabasak.bookstorebd.core.data.cache.ResponseCache
import io.github.utshabasak.bookstorebd.core.datastore.DeviceKeys
import io.github.utshabasak.bookstorebd.core.datastore.SessionStore
import io.github.utshabasak.bookstorebd.core.model.SessionUser
import io.github.utshabasak.bookstorebd.core.network.AccessTokenProvider
import io.github.utshabasak.bookstorebd.core.network.Mappers
import io.github.utshabasak.bookstorebd.core.network.NetworkConfig
import io.github.utshabasak.bookstorebd.core.network.SessionApi
import io.github.utshabasak.bookstorebd.core.network.SessionRefresher
import io.github.utshabasak.bookstorebd.core.network.dto.AppSignInResponse
import io.github.utshabasak.bookstorebd.core.network.dto.RefreshRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

/**
 * The signed-in session.
 *
 * The access token lives only in memory; the refresh token is kept encrypted
 * by [SessionStore]. Renewing the session signs the request with the phone's
 * keystore key ([DeviceKeys]), which the server checks against the public key
 * it was given at sign-in, so a refresh token copied off the phone is useless.
 *
 * Renewals are serialised: when several requests fail at once, the first
 * renews the session and the others use its new token.
 */
@Singleton
class SessionManager
    @Inject
    constructor(
        private val store: SessionStore,
        private val sessionApi: SessionApi,
        private val deviceKeys: DeviceKeys,
        private val mappers: Mappers,
        private val cache: ResponseCache,
        config: NetworkConfig,
        @Dispatcher(BookStoreDispatchers.IO) private val io: CoroutineDispatcher,
    ) : AccessTokenProvider,
        SessionRefresher,
        UserSession {
        @Volatile private var accessToken: String? = null
        private val lock = ReentrantLock()

        /** The path the server checks the signature against: the API prefix and the refresh endpoint. */
        private val refreshPath = URI(config.apiBaseUrl).path.trimEnd('/') + "/" + SessionApi.REFRESH_PATH

        override val user: Flow<SessionUser?> = store.user.distinctUntilChanged()

        override suspend fun currentUser(): SessionUser? = store.user.first()

        override fun currentAccessToken(): String? = accessToken

        /** Keeps a session the server has just started (sign-in, sign-up, verify, Google). */
        suspend fun start(response: AppSignInResponse): SessionUser? {
            val token = response.token
            val refresh = response.refreshToken
            val user = response.user?.let(mappers::user)
            if (token == null || refresh == null || user == null) return null
            store.save(user, refresh)
            accessToken = token
            return user
        }

        /**
         * Renews the session now, if there is one: at launch, so the first screen
         * that needs the account does not wait. Returns whether there is a usable
         * session afterwards.
         */
        suspend fun renew(): Boolean = withContext(io) { refreshBlocking(failedToken = null) != null }

        override fun refreshBlocking(failedToken: String?): String? =
            lock.withLock {
                // Another request renewed the session while this one waited.
                accessToken?.let { current -> if (current != failedToken) return current }
                when (val result = refreshNow()) {
                    is Renewal.Renewed -> {
                        result.token
                    }

                    Renewal.Ended -> {
                        endLocally()
                        null
                    }

                    Renewal.Unreachable -> {
                        null
                    }
                }
            }

        private fun refreshNow(): Renewal {
            val refreshToken = runBlocking { store.refreshToken() } ?: return Renewal.Ended
            val timestamp = System.currentTimeMillis().toString()
            val signature = deviceKeys.sign(payload(timestamp, refreshToken)) ?: return Renewal.Ended
            return try {
                val response = sessionApi.refresh(RefreshRequest(refreshToken), timestamp, signature).execute()
                val body = response.body()
                when {
                    response.isSuccessful && body != null -> {
                        val user = runBlocking { start(body) }
                        if (user != null) Renewal.Renewed(body.token!!) else Renewal.Ended
                    }

                    response.code() == HTTP_UNAUTHORIZED -> {
                        Renewal.Ended
                    }

                    else -> {
                        Renewal.Unreachable
                    }
                }
            } catch (_: IOException) {
                // No connection: keep the session, and try again next time.
                Renewal.Unreachable
            }
        }

        /** What the phone signs: method, path, time and a hash of the token, one per line (server/utils/deviceKey.ts). */
        private fun payload(
            timestamp: String,
            refreshToken: String,
        ): ByteArray {
            val tokenHash = MessageDigest.getInstance("SHA-256").digest(refreshToken.toByteArray()).joinToString("") { "%02x".format(it) }
            return listOf("POST", refreshPath, timestamp, tokenHash).joinToString("\n").toByteArray()
        }

        /** Signs out: ends the session on the server when it can be reached, and forgets it here either way. */
        suspend fun signOut() {
            val refreshToken = store.refreshToken()
            if (refreshToken != null) {
                runCatching { withContext(io) { sessionApi.signOut(RefreshRequest(refreshToken)) } }
            }
            endLocally()
        }

        /** The refresh token, for "sign out everywhere else", which keeps this session. */
        suspend fun refreshToken(): String? = store.refreshToken()

        private fun endLocally() {
            accessToken = null
            runBlocking {
                val signedOut = store.user.first()
                store.clear()
                signedOut?.let { cache.forgetUser(it.id) }
            }
            deviceKeys.deleteKey()
        }

        private sealed interface Renewal {
            data class Renewed(
                val token: String,
            ) : Renewal

            /** The session is over: signed out elsewhere, expired, or the key is gone. */
            data object Ended : Renewal

            /** The server could not be reached; the session may still be good. */
            data object Unreachable : Renewal
        }

        private companion object {
            const val HTTP_UNAUTHORIZED = 401
        }
    }
