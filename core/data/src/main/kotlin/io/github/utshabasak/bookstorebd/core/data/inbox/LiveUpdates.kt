package io.github.utshabasak.bookstorebd.core.data.inbox

import io.github.utshabasak.bookstorebd.core.common.ApplicationScope
import io.github.utshabasak.bookstorebd.core.data.session.SessionManager
import io.github.utshabasak.bookstorebd.core.model.AppNotification
import io.github.utshabasak.bookstorebd.core.model.ChatMessage
import io.github.utshabasak.bookstorebd.core.network.InboxMappers
import io.github.utshabasak.bookstorebd.core.network.NetworkConfig
import io.github.utshabasak.bookstorebd.core.network.di.BaseClient
import io.github.utshabasak.bookstorebd.core.network.dto.ChatMessageDto
import io.github.utshabasak.bookstorebd.core.network.dto.NotificationDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Something that arrived while the app was open. */
sealed interface LiveEvent {
    data class Message(
        val message: ChatMessage,
    ) : LiveEvent

    data class Notification(
        val notification: AppNotification,
    ) : LiveEvent

    /** A push said there is news, without the details: lists should refresh. */
    data object Changed : LiveEvent
}

/**
 * The website's live channel, kept open while the app is in front: new chat
 * messages and notifications arrive at once, as they do on the website. In the
 * background, push notifications take over and this is closed.
 *
 * The server speaks Socket.IO 4 over a WebSocket. Only the little the app
 * needs is spoken here, over the app's own HTTP client: the handshake with the
 * access token, the heartbeat, and the two events. The server sends nothing a
 * connection did not ask for; it only delivers to the person's own room.
 */
@Singleton
class LiveUpdates
    @Inject
    constructor(
        @BaseClient base: OkHttpClient,
        private val config: NetworkConfig,
        private val session: SessionManager,
        private val mappers: InboxMappers,
        private val json: Json,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val client = base.newBuilder().readTimeout(0, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).build()
        private val events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = BUFFER)
        val updates: SharedFlow<LiveEvent> = events.asSharedFlow()

        private val lock = Any()
        private var socket: WebSocket? = null
        private var wanted = false
        private var attempt = 0
        private var retry: Job? = null
        private var watchdog: Job? = null
        private var quietLimitMs = DEFAULT_QUIET_MS

        /** Opens the channel, and keeps it open until [stop]; does nothing while signed out. */
        fun start() {
            synchronized(lock) {
                wanted = true
                if (socket == null && retry?.isActive != true) connect()
            }
        }

        fun stop() {
            synchronized(lock) {
                wanted = false
                retry?.cancel()
                watchdog?.cancel()
                socket?.close(NORMAL_CLOSURE, null)
                socket = null
                attempt = 0
            }
        }

        /** For news from elsewhere, such as a push: screens listening refresh. */
        fun announce(event: LiveEvent) {
            events.tryEmit(event)
        }

        private fun connect() {
            val token = session.currentAccessToken()
            if (token == null) {
                // Straight after launch the access token is renewed first.
                scope.launch {
                    if (session.renew()) synchronized(lock) { if (wanted && socket == null) open(session.currentAccessToken()) }
                }
                return
            }
            open(token)
        }

        private fun open(token: String?) {
            if (token == null) return
            val request = Request.Builder().url(socketUrl(config.siteUrl)).build()
            socket = client.newWebSocket(request, Listener(token))
        }

        private fun scheduleReconnect(renewFirst: Boolean) {
            synchronized(lock) {
                socket = null
                watchdog?.cancel()
                if (!wanted) return
                val wait = (BASE_RETRY_MS shl attempt.coerceAtMost(MAX_SHIFT)).coerceAtMost(MAX_RETRY_MS)
                attempt += 1
                retry?.cancel()
                retry =
                    scope.launch {
                        if (renewFirst) session.renew()
                        delay(wait)
                        synchronized(lock) { if (wanted && socket == null) connect() }
                    }
            }
        }

        /** Closes a connection that has gone quiet for longer than the server's heartbeat allows. */
        private fun rearmWatchdog(webSocket: WebSocket) {
            watchdog?.cancel()
            watchdog =
                scope.launch {
                    delay(quietLimitMs)
                    webSocket.cancel()
                }
        }

        private inner class Listener(
            private val token: String,
        ) : WebSocketListener() {
            override fun onMessage(
                webSocket: WebSocket,
                text: String,
            ) {
                rearmWatchdog(webSocket)
                when {
                    text.startsWith(ENGINE_OPEN) -> {
                        readHeartbeat(text.drop(1))
                        webSocket.send(CONNECT + buildJsonObject { put("token", token) })
                    }

                    text == ENGINE_PING -> {
                        webSocket.send(ENGINE_PONG)
                    }

                    text.startsWith(CONNECT) -> {
                        synchronized(lock) { attempt = 0 }
                    }

                    text.startsWith(CONNECT_ERROR) -> {
                        // Usually an access token that ran out: renew it, then try again.
                        webSocket.close(NORMAL_CLOSURE, null)
                        scheduleReconnect(renewFirst = true)
                    }

                    text.startsWith(EVENT) -> {
                        runCatching { dispatch(text.drop(EVENT.length)) }
                    }

                    text.startsWith(DISCONNECT) -> {
                        webSocket.close(NORMAL_CLOSURE, null)
                    }
                }
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) = handleEnd(webSocket)

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?,
            ) = handleEnd(webSocket)

            private fun handleEnd(webSocket: WebSocket) {
                val current = synchronized(lock) { socket === webSocket }
                if (current) scheduleReconnect(renewFirst = false)
            }
        }

        private fun readHeartbeat(open: String) {
            val handshake = runCatching { json.parseToJsonElement(open) as JsonObject }.getOrNull() ?: return
            val interval = handshake["pingInterval"]?.jsonPrimitive?.longOrNull ?: return
            val timeout = handshake["pingTimeout"]?.jsonPrimitive?.longOrNull ?: return
            quietLimitMs = interval + timeout
        }

        private fun dispatch(payload: String) {
            parseEvent(payload)?.let(events::tryEmit)
        }

        /** `["receive_message", {…}]` or `["notification", {…}]`; null for anything else. */
        private fun parseEvent(payload: String): LiveEvent? {
            val array = json.parseToJsonElement(payload) as? JsonArray
            val name = array?.getOrNull(0)?.jsonPrimitive?.content
            val body = array?.getOrNull(1) ?: return null
            return when (name) {
                "receive_message" -> {
                    LiveEvent.Message(mappers.message(json.decodeFromJsonElement(ChatMessageDto.serializer(), body)))
                }

                "notification" -> {
                    LiveEvent.Notification(
                        mappers.notification(json.decodeFromJsonElement(NotificationDto.serializer(), body)),
                    )
                }

                else -> {
                    null
                }
            }
        }

        internal companion object {
            const val ENGINE_OPEN = "0"
            const val ENGINE_PING = "2"
            const val ENGINE_PONG = "3"
            const val CONNECT = "40"
            const val DISCONNECT = "41"
            const val EVENT = "42"
            const val CONNECT_ERROR = "44"
            const val NORMAL_CLOSURE = 1000
            const val BUFFER = 64
            const val BASE_RETRY_MS = 2_000L
            const val MAX_RETRY_MS = 60_000L
            const val MAX_SHIFT = 5
            const val DEFAULT_QUIET_MS = 45_000L

            /** https://site → wss://site/socket.io/?EIO=4&transport=websocket */
            fun socketUrl(siteUrl: String): String {
                val origin = siteUrl.trimEnd('/')
                val ws =
                    if (origin.startsWith("https://")) {
                        "wss://" + origin.removePrefix("https://")
                    } else {
                        "ws://" +
                            origin.removePrefix("http://")
                    }
                return "$ws/socket.io/?EIO=4&transport=websocket"
            }
        }
    }
