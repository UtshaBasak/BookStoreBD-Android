package io.github.utshabasak.bookstorebd.feature.wanted

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.CommunityRepository
import io.github.utshabasak.bookstorebd.core.data.repository.PageLoadException
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.WantedDraft
import io.github.utshabasak.bookstorebd.core.model.WantedItem
import io.github.utshabasak.bookstorebd.core.model.WantedQuery
import io.github.utshabasak.bookstorebd.core.model.WantedResult
import io.github.utshabasak.bookstorebd.core.model.WantedSort
import io.github.utshabasak.bookstorebd.core.model.WantedStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WantedUiState(
    val query: WantedQuery = WantedQuery(),
    val searchText: String = "",
    /** Joins and leaves made here, shown at once while the list catches up. */
    val mine: Map<String, Boolean> = emptyMap(),
    val form: WantedDraft? = null,
    val asking: Boolean = false,
    val titleProblem: Boolean = false,
    /** Bumped after asking, so the list reloads. */
    val version: Int = 0,
)

sealed interface WantedEvent {
    data class Message(
        val text: String,
        val bookId: String? = null,
    ) : WantedEvent

    data class Failed(
        val error: AppError,
    ) : WantedEvent

    data object SignInNeeded : WantedEvent
}

/**
 * The Wanted board: books people are looking for. Anyone can browse; asking,
 * joining and "Mine" need an account, as on the website.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel(assistedFactory = WantedViewModel.Factory::class)
class WantedViewModel
    @AssistedInject
    constructor(
        @Assisted ask: String?,
        private val community: CommunityRepository,
        private val session: UserSession,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(ask: String?): WantedViewModel
        }

        private val state = MutableStateFlow(WantedUiState(form = ask?.let { WantedDraft(title = it) }))
        val uiState: StateFlow<WantedUiState> = state.asStateFlow()

        val signedIn: StateFlow<Boolean> = session.user.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

        val items: Flow<PagingData<WantedItem>> =
            state
                .map { it.query to it.version }
                .distinctUntilChanged()
                .debounce { (query, _) -> if (query.search.isEmpty()) 0L else SEARCH_DELAY_MS }
                .flatMapLatest { (query, _) -> community.wanted(query) }
                .cachedIn(viewModelScope)

        private val events = Channel<WantedEvent>(Channel.BUFFERED)
        val eventFlow: Flow<WantedEvent> = events.receiveAsFlow()

        fun setSearch(text: String) = state.update { it.copy(searchText = text, query = it.query.copy(search = text.trim())) }

        fun setStatus(status: WantedStatus) = state.update { it.copy(query = it.query.copy(status = status)) }

        fun setSort(sort: WantedSort) = state.update { it.copy(query = it.query.copy(sort = sort)) }

        fun setMineOnly(mine: Boolean) {
            if (mine && !signedIn.value) {
                viewModelScope.launch { events.send(WantedEvent.SignInNeeded) }
                return
            }
            state.update { it.copy(query = it.query.copy(mineOnly = mine)) }
        }

        fun openForm() {
            if (!signedIn.value) {
                viewModelScope.launch { events.send(WantedEvent.SignInNeeded) }
                return
            }
            state.update { it.copy(form = it.form ?: WantedDraft(title = "")) }
        }

        fun closeForm() = state.update { it.copy(form = null, titleProblem = false) }

        fun editForm(change: (WantedDraft) -> WantedDraft) =
            state.update { current ->
                current.copy(form = current.form?.let(change), titleProblem = false)
            }

        fun ask() {
            val draft = state.value.form ?: return
            if (draft.title.trim().length < MIN_TITLE) {
                state.update { it.copy(titleProblem = true) }
                return
            }
            if (!signedIn.value) {
                viewModelScope.launch { events.send(WantedEvent.SignInNeeded) }
                return
            }
            viewModelScope.launch {
                state.update { it.copy(asking = true) }
                val result = community.askForBook(draft)
                state.update { it.copy(asking = false) }
                when (result) {
                    is Outcome.Success -> {
                        state.update { it.copy(form = null, version = it.version + 1) }
                        val answer = result.value
                        events.send(WantedEvent.Message(answer.message, (answer as? WantedResult.Listed)?.bookId))
                    }

                    is Outcome.Failure -> {
                        events.send(WantedEvent.Failed(result.error))
                    }
                }
            }
        }

        /** "I want this too", or taking it back; shown at once and undone if the server says no. */
        fun toggle(item: WantedItem) {
            if (!signedIn.value) {
                viewModelScope.launch { events.send(WantedEvent.SignInNeeded) }
                return
            }
            val wanted = state.value.mine[item.id] ?: item.wantedByMe
            state.update { it.copy(mine = it.mine + (item.id to !wanted)) }
            viewModelScope.launch {
                val result = if (wanted) community.leave(item.id) else community.join(item.id)
                if (result is Outcome.Failure) {
                    state.update { it.copy(mine = it.mine + (item.id to wanted)) }
                    events.send(WantedEvent.Failed(result.error))
                }
            }
        }

        private companion object {
            const val SEARCH_DELAY_MS = 350L
            const val MIN_TITLE = 2
        }
    }

internal fun Throwable.appError(): AppError = (this as? PageLoadException)?.error ?: AppError.Unexpected(this)
