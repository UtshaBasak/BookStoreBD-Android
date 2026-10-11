package io.github.utshabasak.bookstorebd.feature.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.image.ImagePreparer
import io.github.utshabasak.bookstorebd.core.data.image.ImagePurpose
import io.github.utshabasak.bookstorebd.core.data.inbox.ActiveConversation
import io.github.utshabasak.bookstorebd.core.data.inbox.ChatRepository
import io.github.utshabasak.bookstorebd.core.data.inbox.LiveEvent
import io.github.utshabasak.bookstorebd.core.data.inbox.LiveUpdates
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.ChatMessage
import io.github.utshabasak.bookstorebd.core.model.Conversation
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.ui.navigation.ChatRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MessagesUiState(
    val signedIn: Boolean? = null,
    val conversations: List<Conversation>? = null,
    val refreshing: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class MessagesViewModel
    @javax.inject.Inject
    constructor(
        private val repository: ChatRepository,
        live: LiveUpdates,
        session: UserSession,
    ) : ViewModel() {
        private val refreshing = MutableStateFlow(false)
        private val error = MutableStateFlow<AppError?>(null)

        val uiState: StateFlow<MessagesUiState> =
            combine(session.user, repository.conversations, refreshing, error) { user, list, busy, problem ->
                MessagesUiState(signedIn = user != null, conversations = list, refreshing = busy, error = problem)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), MessagesUiState())

        init {
            // A new message elsewhere moves its conversation up; the counts are kept app-wide.
            live.updates.filterIsInstance<LiveEvent.Changed>().onEach { refresh() }.launchIn(viewModelScope)
        }

        fun refresh() {
            viewModelScope.launch {
                refreshing.value = true
                error.value = (repository.refreshConversations() as? Outcome.Failure)?.error
                refreshing.value = false
            }
        }

        private companion object {
            const val STOP_MS = 5_000L
        }
    }

/** A message on screen: sent, or still on its way ([pending]), or not sent ([failed]). */
data class Bubble(
    val message: ChatMessage,
    val mine: Boolean,
    val pending: Boolean = false,
    val failed: Boolean = false,
    /** For a photo still on its way: the picture as chosen, shown until it is sent. */
    val localPhoto: Uri? = null,
)

data class ChatUiState(
    val me: String = "",
    val bubbles: List<Bubble> = emptyList(),
    val loading: Boolean = true,
    val loadingOlder: Boolean = false,
    val hasOlder: Boolean = false,
    val error: AppError? = null,
    val draft: String = "",
)

sealed interface ChatEvent {
    data class Failed(
        val error: AppError,
    ) : ChatEvent

    data object Deleted : ChatEvent
}

@HiltViewModel(assistedFactory = ChatViewModel.Factory::class)
class ChatViewModel
    @AssistedInject
    constructor(
        @Assisted val route: ChatRoute,
        private val repository: ChatRepository,
        private val images: ImagePreparer,
        private val active: ActiveConversation,
        live: LiveUpdates,
        private val session: UserSession,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(route: ChatRoute): ChatViewModel
        }

        private val state = MutableStateFlow(ChatUiState())
        val uiState: StateFlow<ChatUiState> = state.asStateFlow()

        private val events = Channel<ChatEvent>(Channel.BUFFERED)
        val eventFlow: Flow<ChatEvent> = events.receiveAsFlow()

        /** The other person's name, from the conversation list once known. */
        val name: StateFlow<String> =
            repository.conversations
                .map { list -> list?.firstOrNull { it.email == route.email }?.name ?: route.name }
                .stateIn(viewModelScope, SharingStarted.Eagerly, route.name)

        private var page = 1
        private var pendingId = 0

        init {
            load()
            live.updates
                .filterIsInstance<LiveEvent.Message>()
                .onEach { event ->
                    val message = event.message
                    if (message.sender.equals(route.email, ignoreCase = true)) {
                        state.update { it.copy(bubbles = it.bubbles + Bubble(message, mine = false)) }
                        if (active.isOpen(route.email)) repository.markRead(route.email)
                    }
                }.launchIn(viewModelScope)
        }

        fun load() {
            viewModelScope.launch {
                val me = session.currentUser()?.email.orEmpty()
                state.update { it.copy(me = me, loading = true, error = null) }
                when (val result = repository.messages(route.email, page = 1)) {
                    is Outcome.Success -> {
                        page = 1
                        state.update { current ->
                            current.copy(
                                bubbles = result.value.messages.map { Bubble(it, mine = it.sender.equals(me, ignoreCase = true)) },
                                hasOlder = result.value.hasOlder,
                                loading = false,
                            )
                        }
                        repository.markRead(route.email)
                    }

                    is Outcome.Failure -> {
                        state.update { it.copy(loading = false, error = result.error) }
                    }
                }
            }
        }

        fun loadOlder() {
            val current = state.value
            if (current.loadingOlder || !current.hasOlder) return
            viewModelScope.launch {
                state.update { it.copy(loadingOlder = true) }
                when (val result = repository.messages(route.email, page = page + 1)) {
                    is Outcome.Success -> {
                        page += 1
                        val known = state.value.bubbles.map { it.message.id }.toSet()
                        val older =
                            result.value.messages.filterNot {
                                it.id in known
                            }.map { Bubble(it, mine = it.sender.equals(current.me, true)) }
                        state.update { it.copy(bubbles = older + it.bubbles, hasOlder = result.value.hasOlder, loadingOlder = false) }
                    }

                    is Outcome.Failure -> {
                        state.update { it.copy(loadingOlder = false) }
                        events.send(ChatEvent.Failed(result.error))
                    }
                }
            }
        }

        fun setDraft(text: String) {
            state.update { it.copy(draft = text.take(MAX_LENGTH)) }
        }

        fun sendText() {
            val text = state.value.draft.trim()
            if (text.isEmpty()) return
            state.update { it.copy(draft = "") }
            send(text, photo = null)
        }

        fun sendPhoto(uri: Uri) = send(state.value.draft.trim().also { if (it.isNotEmpty()) state.update { s -> s.copy(draft = "") } }, uri)

        /** Tries a message that did not go again. */
        fun retry(bubble: Bubble) {
            state.update { it.copy(bubbles = it.bubbles - bubble) }
            send(bubble.message.text, bubble.localPhoto)
        }

        private fun send(
            text: String,
            photo: Uri?,
        ) {
            val localId = "local-${pendingId++}"
            val placeholder =
                Bubble(
                    message = ChatMessage(localId, state.value.me, route.email, text, null, "", read = false),
                    mine = true,
                    pending = true,
                    localPhoto = photo,
                )
            state.update { it.copy(bubbles = it.bubbles + placeholder) }
            viewModelScope.launch {
                val upload = photo?.let { images.prepare(it, ImagePurpose.Photo) }
                val result =
                    if (photo != null && upload == null) {
                        Outcome.Failure(AppError.Unexpected(IllegalStateException("The photo could not be read")))
                    } else {
                        repository.send(route.email, text, upload)
                    }
                state.update { current ->
                    current.copy(
                        bubbles =
                            current.bubbles.map {
                                if (it.message.id != localId) {
                                    it
                                } else if (result is Outcome.Success) {
                                    Bubble(result.value, mine = true)
                                } else {
                                    it.copy(pending = false, failed = true)
                                }
                            },
                    )
                }
                if (result is Outcome.Failure) events.send(ChatEvent.Failed(result.error))
            }
        }

        fun delete() {
            viewModelScope.launch {
                when (val result = repository.delete(route.email)) {
                    is Outcome.Success -> events.send(ChatEvent.Deleted)
                    is Outcome.Failure -> events.send(ChatEvent.Failed(result.error))
                }
            }
        }

        /** The conversation is on screen: its messages are read, and not also shown as phone notifications. */
        fun onScreen(visible: Boolean) {
            if (visible) {
                active.enter(route.email)
                viewModelScope.launch { repository.markRead(route.email) }
            } else {
                active.leave(route.email)
            }
        }

        override fun onCleared() {
            active.leave(route.email)
        }

        internal companion object {
            /** As long as a chat message may be on the website. */
            const val MAX_LENGTH = 2_000
        }
    }
