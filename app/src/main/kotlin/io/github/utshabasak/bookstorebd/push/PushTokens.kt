package io.github.utshabasak.bookstorebd.push

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.utshabasak.bookstorebd.BuildConfig
import io.github.utshabasak.bookstorebd.core.common.ApplicationScope
import io.github.utshabasak.bookstorebd.core.data.inbox.NotificationRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * This phone's Firebase push token, and only while someone is signed in.
 *
 * Firebase is started here, from the values the build read out of
 * google-services.json, with automatic start-up switched off in the manifest:
 * a phone nobody has signed in on never asks Google for a token. On signing
 * in the token is created and filed under the session (the server removes it
 * when the session ends); on signing out it is deleted here as well.
 */
@Singleton
class PushTokens
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val session: UserSession,
        private val notifications: NotificationRepository,
        private val notifier: PushNotifier,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val configured = BuildConfig.FIREBASE_APP_ID.isNotBlank()

        fun start() {
            if (!configured || !initialiseFirebase()) return
            val signedIn = session.user.map { it?.id }.distinctUntilChanged()
            signedIn.onEach { id -> if (id != null) register() }.launchIn(scope)
            signedIn.drop(1).onEach { id -> if (id == null) forget() }.launchIn(scope)
        }

        /**
         * Registers this phone with Firebase and files its token. Firebase
         * announces later changes in [PushService.onRegistered]; after signing
         * out and in again it does not announce the new token, so it is read
         * here as well.
         */
        private suspend fun register() {
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = true
            messaging.register().awaitOrNull()
            @Suppress("DEPRECATION")
            val token = messaging.token.awaitOrNull()
            notifications.registerPushToken(token)
        }

        /** A token Firebase has just issued, filed under the current session. */
        suspend fun registered(token: String) {
            if (!configured || session.currentUser() == null) return
            notifications.registerPushToken(token)
        }

        private suspend fun forget() {
            notifier.clearAll()
            notifications.forgetPushToken()
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = false
            messaging.unregister().awaitOrNull()
        }

        private fun initialiseFirebase(): Boolean {
            if (FirebaseApp.getApps(context).isNotEmpty()) return true
            val options =
                FirebaseOptions
                    .Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .build()
            return runCatching { FirebaseApp.initializeApp(context, options) }.isSuccess
        }
    }

private suspend fun <T> Task<T>.awaitOrNull(): T? =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task -> continuation.resume(if (task.isSuccessful) task.result else null) }
    }
