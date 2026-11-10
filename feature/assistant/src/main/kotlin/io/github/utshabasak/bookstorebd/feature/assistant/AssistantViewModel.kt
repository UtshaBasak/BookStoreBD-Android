package io.github.utshabasak.bookstorebd.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.AssistantRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CommunityRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.AssistantReply
import io.github.utshabasak.bookstorebd.core.model.ChatTurn
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.WantedDraft
import io.github.utshabasak.bookstorebd.core.ui.navigation.AssistantRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One bubble: the person's question, or the assistant's answer with what the shop found for it. */
data class AssistantMessage(
    val id: Int,
    val fromUser: Boolean,
    val text: String,
    val reply: AssistantReply? = null,
    /** A thumbs up or down has been given for this answer; it is given once. */
    val feedbackSent: Boolean = false,
    /** The Wanted board's request is being posted. */
    val askingWanted: Boolean = false,
    /** What the Wanted board said, shown in place of the button. */
    val wantedMessage: String? = null,
)

data class AssistantUiState(
    val messages: List<AssistantMessage> = emptyList(),
    val input: String = "",
    /** A question is on its way; one at a time. */
    val pending: Boolean = false,
    /** Why the last question got no answer; the question is back in [input]. */
    val error: AppError? = null,
    /** False once the server says the assistant was switched off. */
    val available: Boolean = true,
    val signedIn: Boolean = false,
    /** The website's address, for pages the app does not have, and its privacy policy. */
    val site: String = "",
    val privacy: String = "",
) {
    val canSend: Boolean get() = available && !pending && input.isNotBlank()

    /** The person's last few questions, oldest first, for a summary handed to a seller. */
    fun lastQuestions(count: Int = SUMMARY_QUESTIONS): List<String> = messages.filter { it.fromUser }.takeLast(count).map { it.text }

    private companion object {
        const val SUMMARY_QUESTIONS = 3
    }
}

sealed interface AssistantEvent {
    /** Something beside the conversation did not work: the Wanted board's request. */
    data class Failed(
        val error: AppError,
    ) : AssistantEvent
}

/**
 * The conversation with the shopping assistant. It lives here, in memory, for
 * as long as the screen is on the back stack, and is never written to the
 * phone. A question is sent once: only the person's own tap sends again.
 */
@HiltViewModel(assistedFactory = AssistantViewModel.Factory::class)
class AssistantViewModel
    @AssistedInject
    constructor(
        @Assisted route: AssistantRoute,
        private val assistant: AssistantRepository,
        private val community: CommunityRepository,
        private val configs: ConfigRepository,
        session: UserSession,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(route: AssistantRoute): AssistantViewModel
        }

        private val state = MutableStateFlow(AssistantUiState(input = route.prefill.take(AssistantRepository.MAX_MESSAGE)))
        val uiState: StateFlow<AssistantUiState> = state.asStateFlow()

        private val events = Channel<AssistantEvent>(Channel.BUFFERED)
        val eventFlow: Flow<AssistantEvent> = events.receiveAsFlow()

        private var nextId = 0

        init {
            session.user.onEach { user -> state.update { it.copy(signedIn = user != null) } }.launchIn(viewModelScope)
            configs.config
                .onEach { config ->
                    if (config != null) state.update { it.copy(site = config.links.site.trimEnd('/'), privacy = config.links.privacy) }
                }.launchIn(viewModelScope)
            viewModelScope.launch { if (configs.current() == null) configs.refresh() }
        }

        fun setInput(text: String) {
            state.update { it.copy(input = text.take(AssistantRepository.MAX_MESSAGE)) }
        }

        /** Asks what is in the box. */
        fun send() = ask(state.value.input)

        /** Asks [question]: what was typed, or one of the starting questions. */
        fun ask(question: String) {
            val text = question.trim().take(AssistantRepository.MAX_MESSAGE)
            val current = state.value
            if (text.isEmpty() || current.pending || !current.available) return
            val history = current.messages.map { ChatTurn(it.fromUser, it.text) }
            val asked = AssistantMessage(nextId++, fromUser = true, text = text)
            state.update { it.copy(messages = (it.messages + asked).takeLast(MAX_MESSAGES), input = "", pending = true, error = null) }
            viewModelScope.launch {
                when (val result = assistant.ask(text, history)) {
                    is Outcome.Success -> {
                        val answer = AssistantMessage(nextId++, fromUser = false, text = result.value.text, reply = result.value)
                        state.update { it.copy(messages = (it.messages + answer).takeLast(MAX_MESSAGES), pending = false) }
                    }

                    is Outcome.Failure -> {
                        // The question goes back in the box, for the person to send again if they wish.
                        state.update {
                            it.copy(
                                messages = it.messages.filterNot { message -> message.id == asked.id },
                                input = it.input.ifBlank { text },
                                pending = false,
                                error = result.error,
                                available = it.available && !result.error.isSwitchedOff(),
                            )
                        }
                    }
                }
            }
        }

        /** A thumbs up or down for one answer, sent once. */
        fun feedback(
            messageId: Int,
            helpful: Boolean,
        ) {
            val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
            val requestId = message.reply?.requestId.orEmpty()
            if (message.feedbackSent || requestId.isEmpty()) return
            update(messageId) { it.copy(feedbackSent = true) }
            viewModelScope.launch { assistant.feedback(requestId, helpful) }
        }

        /** Posts the book this answer offered on the Wanted board, now that the person has asked for it. */
        fun askOnWantedBoard(messageId: Int) {
            val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
            val offer = message.reply?.wanted ?: return
            if (message.askingWanted || message.wantedMessage != null) return
            update(messageId) { it.copy(askingWanted = true) }
            viewModelScope.launch {
                when (val result = community.askForBook(WantedDraft(title = offer.title, author = offer.author))) {
                    is Outcome.Success -> {
                        update(messageId) { it.copy(askingWanted = false, wantedMessage = result.value.message) }
                    }

                    is Outcome.Failure -> {
                        update(messageId) { it.copy(askingWanted = false) }
                        events.send(AssistantEvent.Failed(result.error))
                    }
                }
            }
        }

        /** Clears the conversation. */
        fun startOver() {
            if (state.value.pending) return
            state.update { it.copy(messages = emptyList(), error = null) }
        }

        fun dismissError() = state.update { it.copy(error = null) }

        private fun update(
            messageId: Int,
            change: (AssistantMessage) -> AssistantMessage,
        ) = state.update { current -> current.copy(messages = current.messages.map { if (it.id == messageId) change(it) else it }) }

        private fun AppError.isSwitchedOff(): Boolean = this is AppError.Rejected && code == AssistantRepository.CODE_OFF

        private companion object {
            /** How much of a conversation is kept while the screen is open. */
            const val MAX_MESSAGES = 30
        }
    }
