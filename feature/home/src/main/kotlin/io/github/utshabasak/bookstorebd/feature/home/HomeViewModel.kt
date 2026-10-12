package io.github.utshabasak.bookstorebd.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.inbox.ChatRepository
import io.github.utshabasak.bookstorebd.core.data.inbox.NotificationRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CartRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CatalogueRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.WishlistRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.AppConfig
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.HomeSections
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.TopPicks
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    /** Null until the shelves have been loaded once; afterwards the last ones received, even offline. */
    val sections: HomeSections? = null,
    val recentlyViewed: List<Book> = emptyList(),
    val latest: List<Book> = emptyList(),
    val topPicks: TopPicks? = null,
    val wishlist: Set<String> = emptySet(),
    /** The books in the cart, for the cart button on every card. */
    val cart: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
    /** Why the last refresh failed; shown in full only while there is nothing else to show. */
    val error: AppError? = null,
    /** The shop's settings: the categories, and the promises the hero lists. Null before the first answer. */
    val config: AppConfig? = null,
) {
    /** The shop has no books at all: Latest books invites the first seller instead, as on the website. */
    val shelfEmpty: Boolean get() = sections != null && latest.isEmpty() && error == null && !isRefreshing

    /** The "Shop by category" row. */
    val categoryTiles get() = categoryTiles(sections?.categories.orEmpty())
}

/** The numbers on the bell and the messages icon; both hidden while signed out. */
data class InboxBadges(
    val signedIn: Boolean = false,
    val notifications: Int = 0,
    val messages: Int = 0,
)

sealed interface HomeEvent {
    data object SignInToSave : HomeEvent

    data object SignInForCart : HomeEvent

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
        private val cart: CartRepository,
        private val session: UserSession,
        notifications: NotificationRepository,
        chats: ChatRepository,
        config: ConfigRepository,
    ) : ViewModel() {
        val inbox: StateFlow<InboxBadges> =
            combine(session.user, notifications.unread, chats.unread) { user, bell, messages ->
                InboxBadges(signedIn = user != null, notifications = bell, messages = messages)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InboxBadges())

        private val refreshing = MutableStateFlow(false)
        private val error = MutableStateFlow<AppError?>(null)
        private val events = Channel<HomeEvent>(Channel.BUFFERED)
        val eventFlow: Flow<HomeEvent> = events.receiveAsFlow()

        private val shelves =
            combine(catalogue.homeSections, catalogue.latestBooks, catalogue.topPicks, catalogue.recentlyViewed, config.config) {
                sections,
                latest,
                picks,
                recent,
                shop,
                ->
                HomeUiState(sections = sections, latest = latest, topPicks = picks, recentlyViewed = recent, config = shop)
            }

        val uiState: StateFlow<HomeUiState> =
            combine(shelves, wishlist.ids, cart.quantities, refreshing, error) { base, saved, inCart, busy, problem ->
                base.copy(wishlist = saved, cart = inCart.keys, isRefreshing = busy, error = problem)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState(isRefreshing = true))

        init {
            refresh()
            // Recently viewed follows what was opened, fetched fresh so prices and stock are current.
            catalogue.recentlyViewedIds
                .distinctUntilChanged()
                .debounce(RECENT_DEBOUNCE_MS)
                .onEach { catalogue.refreshRecentlyViewed(it) }
                .launchIn(viewModelScope)
            // Someone signed in or out: the old picks are no longer theirs.
            session.user
                .map { it?.id }
                .distinctUntilChanged()
                .drop(1)
                .onEach { catalogue.refreshTopPicks(forget = true) }
                .launchIn(viewModelScope)
        }

        fun refresh() {
            if (refreshing.value && uiState.value.sections != null) return
            viewModelScope.launch {
                refreshing.value = true
                val latest = async { catalogue.refreshLatest() }
                val picks = async { catalogue.refreshTopPicks() }
                val result = catalogue.refreshHome()
                latest.await()
                picks.await()
                error.value = (result as? Outcome.Failure)?.error
                if (session.currentUser() != null) wishlist.refresh()
                refreshing.value = false
            }
        }

        /** The cart button on a card: in, or out again, as on the website. */
        fun toggleCart(book: Book) {
            viewModelScope.launch {
                if (session.currentUser() == null) {
                    events.send(HomeEvent.SignInForCart)
                    return@launch
                }
                val result = cart.toggle(book.id, inCart = book.id in uiState.value.cart)
                if (result is Outcome.Failure) events.send(HomeEvent.Failed(result.error))
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
