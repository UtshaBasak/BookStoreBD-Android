package io.github.utshabasak.bookstorebd.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.CatalogueRepository
import io.github.utshabasak.bookstorebd.core.data.repository.WishlistRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.HomeSections
import io.github.utshabasak.bookstorebd.core.model.Outcome
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    /** Null until the shelves have been loaded once; afterwards the last ones received, even offline. */
    val sections: HomeSections? = null,
    val recentlyViewed: List<Book> = emptyList(),
    val wishlist: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
    /** Why the last refresh failed; shown in full only while there is nothing else to show. */
    val error: AppError? = null,
)

sealed interface HomeEvent {
    data object SignInToSave : HomeEvent

    data class Failed(
        val error: AppError,
    ) : HomeEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val catalogue: CatalogueRepository,
        private val wishlist: WishlistRepository,
        private val session: UserSession,
    ) : ViewModel() {
        private val refreshing = MutableStateFlow(false)
        private val error = MutableStateFlow<AppError?>(null)
        private val events = Channel<HomeEvent>(Channel.BUFFERED)
        val eventFlow: Flow<HomeEvent> = events.receiveAsFlow()

        val uiState: StateFlow<HomeUiState> =
            combine(catalogue.homeSections, catalogue.recentlyViewed, wishlist.ids, refreshing, error) {
                sections,
                recent,
                saved,
                busy,
                problem,
                ->
                HomeUiState(sections, recent, saved, busy, problem)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState(isRefreshing = true))

        init {
            refresh()
            // Recently viewed follows what was opened, fetched fresh so prices and stock are current.
            catalogue.recentlyViewedIds
                .distinctUntilChanged()
                .debounce(RECENT_DEBOUNCE_MS)
                .onEach { catalogue.refreshRecentlyViewed(it) }
                .launchIn(viewModelScope)
        }

        fun refresh() {
            if (refreshing.value && uiState.value.sections != null) return
            viewModelScope.launch {
                refreshing.value = true
                val result = catalogue.refreshHome()
                error.value = (result as? Outcome.Failure)?.error
                if (session.currentUser() != null) wishlist.refresh()
                refreshing.value = false
            }
        }

        fun toggleWishlist(book: Book) {
            viewModelScope.launch {
                if (session.currentUser() == null) {
                    events.send(HomeEvent.SignInToSave)
                    return@launch
                }
                val result = if (book.id in uiState.value.wishlist) wishlist.remove(book.id) else wishlist.add(book.id)
                if (result is Outcome.Failure) events.send(HomeEvent.Failed(result.error))
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val RECENT_DEBOUNCE_MS = 300L
        }
    }
