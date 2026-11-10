package io.github.utshabasak.bookstorebd.core.data.repository

import io.github.utshabasak.bookstorebd.core.data.util.RequestLanguage
import io.github.utshabasak.bookstorebd.core.datastore.SettingsStore
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.AssistantReply
import io.github.utshabasak.bookstorebd.core.model.ChatTurn
import io.github.utshabasak.bookstorebd.core.model.FeedbackReason
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.map
import io.github.utshabasak.bookstorebd.core.network.AssistantApi
import io.github.utshabasak.bookstorebd.core.network.Mappers
import io.github.utshabasak.bookstorebd.core.network.apiCall
import io.github.utshabasak.bookstorebd.core.network.assistantReply
import io.github.utshabasak.bookstorebd.core.network.dto.ApiErrorDto
import io.github.utshabasak.bookstorebd.core.network.dto.AssistantFeedbackRequest
import io.github.utshabasak.bookstorebd.core.network.dto.AssistantReplyDto
import io.github.utshabasak.bookstorebd.core.network.dto.AssistantRequest
import io.github.utshabasak.bookstorebd.core.network.dto.AssistantTurnDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The shopping assistant. A question goes to the shop's server, once, and the
 * answer comes back; nothing of the conversation is kept here or written to
 * the phone.
 */
@Singleton
class AssistantRepository
    @Inject
    constructor(
        private val api: AssistantApi,
        private val catalogue: CatalogueRepository,
        private val settings: SettingsStore,
        private val language: RequestLanguage,
        private val mappers: Mappers,
        private val json: Json,
    ) {
        private val available = MutableStateFlow(false)

        /** Whether the assistant is offered at all; false until the server says so. */
        val enabled: StateFlow<Boolean> = available.asStateFlow()

        private val checking = Mutex()
        private var checkedAt = 0L

        /**
         * Asks the server whether the assistant is on, at most once a minute.
         * Off on any failure: an older server, no connection, a server in trouble.
         */
        suspend fun isEnabled(): Boolean =
            checking.withLock {
                if (checkedAt != 0L && System.currentTimeMillis() - checkedAt < FRESH_MS) return@withLock available.value
                val result = apiCall(json) { api.config() }
                checkedAt = System.currentTimeMillis()
                available.value = (result as? Outcome.Success)?.value?.enabled == true
                available.value
            }

        /**
         * Asks [message], with the last few [history] turns for context. The
         * books opened lately go along only while the person lets the app
         * remember them, the same choice that keeps Recently viewed.
         */
        suspend fun ask(
            message: String,
            history: List<ChatTurn>,
        ): Outcome<AssistantReply> {
            val seen =
                if (settings.rememberViewed.first()) catalogue.recentlyViewedIds.first().take(MAX_SEEN).ifEmpty { null } else null
            val request =
                AssistantRequest(
                    message = message.trim().take(MAX_MESSAGE),
                    history =
                        history.takeLast(MAX_TURNS).map { turn ->
                            AssistantTurnDto(if (turn.fromUser) "user" else "assistant", turn.text.take(MAX_TURN_TEXT))
                        },
                    seen = seen,
                )
            var switchedOff: AppError? = null
            val result =
                apiCall<AssistantReplyDto?>(json) {
                    try {
                        api.chat(language.tag(), request)
                    } catch (http: HttpException) {
                        if (http.code() != HTTP_UNAVAILABLE) throw http
                        switchedOff = unavailable(http)
                        null
                    }
                }
            switchedOff?.let { return Outcome.Failure(it) }
            return when (result) {
                is Outcome.Failure -> {
                    result
                }

                is Outcome.Success -> {
                    result.value?.let { Outcome.Success(mappers.assistantReply(it)) }
                        ?: Outcome.Failure(AppError.Server(HTTP_UNAVAILABLE))
                }
            }
        }

        /** A thumbs up or down for one answer, by its [requestId]; no text is sent. */
        suspend fun feedback(
            requestId: String,
            helpful: Boolean,
            reason: FeedbackReason? = null,
        ): Outcome<Unit> =
            apiCall(json) {
                val response = api.feedback(language.tag(), AssistantFeedbackRequest(requestId, helpful, reason?.apiValue))
                if (!response.isSuccessful) throw HttpException(response)
            }.map { }

        /**
         * A 503 keeps the server's own sentence when it says the assistant was
         * switched off, and the entry points go with it.
         */
        private fun unavailable(http: HttpException): AppError {
            val body = runCatching { http.response()?.errorBody()?.string() }.getOrNull()
            val parsed = body?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString<ApiErrorDto>(it) }.getOrNull() }
            if (parsed?.code != CODE_OFF) return AppError.Server(HTTP_UNAVAILABLE)
            available.value = false
            checkedAt = System.currentTimeMillis()
            return AppError.Rejected(status = HTTP_UNAVAILABLE, message = parsed.message?.takeIf { it.isNotBlank() }, code = CODE_OFF)
        }

        companion object {
            /** The server's code for an assistant that was switched off. */
            const val CODE_OFF = "assistant-off"

            /** The longest question the server takes. */
            const val MAX_MESSAGE = 500

            /** How many earlier turns go back with a question, as on the website. */
            const val MAX_TURNS = 6
            private const val MAX_TURN_TEXT = 1500
            private const val MAX_SEEN = 20
            private const val FRESH_MS = 60_000L
            private const val HTTP_UNAVAILABLE = 503
        }
    }
