package io.github.utshabasak.bookstorebd.feature.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.BookRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CartRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CatalogueRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CommunityRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.WishlistRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.BookDetail
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.Review
import io.github.utshabasak.bookstorebd.core.model.ReviewPage
import io.github.utshabasak.bookstorebd.core.model.ReviewTarget
import io.github.utshabasak.bookstorebd.core.model.SellerSummary
import io.github.utshabasak.bookstorebd.core.model.getOrNull
import io.github.utshabasak.bookstorebd.core.model.map
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookUiState(
    val detail: BookDetail? = null,
    val loading: Boolean = true,
    val error: AppError? = null,
    val reviews: ReviewPage? = null,
    /** The signed-in person's address, to tell their own review from others'. */
    val viewerEmail: String? = null,
    val seller: SellerSummary? = null,
    val inWishlist: Boolean = false,
    /** Whether the person asked to hear when it is back; null when not known (signed out, or in stock). */
    val requested: Boolean? = null,
    val quantity: Int = 1,
    val adding: Boolean = false,
    val siteUrl: String? = null,
)

sealed interface BookEvent {
    data object SignInRequired : BookEvent

    data object AddedToCart : BookEvent

    data class RequestChanged(
        val requested: Boolean,
    ) : BookEvent

    data class Failed(
        val error: AppError,
    ) : BookEvent

    data class Message(
        val text: String,
    ) : BookEvent
}

/** One book's page: the book, as saved and refreshed, its reviews and seller, and what can be done with it. */
@HiltViewModel(assistedFactory = BookViewModel.Factory::class)
class BookViewModel
    @AssistedInject
    constructor(
        @Assisted private val bookId: String,
        private val books: BookRepository,
        private val cart: CartRepository,
        private val wishlist: WishlistRepository,
        private val session: UserSession,
        catalogue: CatalogueRepository,
        config: ConfigRepository,
        private val community: CommunityRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(bookId: String): BookViewModel
        }

        private val local = MutableStateFlow(BookUiState())
        private val events = Channel<BookEvent>(Channel.BUFFERED)
        val eventFlow: Flow<BookEvent> = events.receiveAsFlow()

        val uiState: StateFlow<BookUiState> =
            combine(local, wishlist.ids, config.config.map { it?.links?.site }, session.user) { state, saved, site, user ->
                state.copy(inWishlist = bookId in saved, siteUrl = site, viewerEmail = user?.email)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BookUiState())

        init {
            viewModelScope.launch { catalogue.recordView(bookId) }
            books
                .book(bookId)
                .filterNotNull()
                .onEach { detail -> local.update { it.copy(detail = detail) } }
                .launchIn(viewModelScope)
            refresh()
            viewModelScope.launch {
                val detail = books.book(bookId).filterNotNull().first()
                local.update { it.copy(seller = books.seller(detail.book.sellerEmail).getOrNull()) }
                if (!detail.book.inStock && session.currentUser() != null) {
                    local.update { it.copy(requested = books.isRequested(bookId).getOrNull()) }
                }
            }
        }

        fun refresh() {
            viewModelScope.launch {
                local.update { it.copy(loading = true) }
                val result = books.refreshBook(bookId)
                local.update { it.copy(loading = false, error = (result as? Outcome.Failure)?.error) }
            }
            refreshReviews()
        }

        /** Also when coming back from writing a review. */
        fun refreshReviews() {
            viewModelScope.launch {
                community.reviews(target).getOrNull()?.let { page -> local.update { it.copy(reviews = page) } }
            }
        }

        fun report(
            review: Review,
            reason: String,
        ) = reviewAction { community.report(target, review.id, reason) }

        fun reply(
            review: Review,
            text: String,
        ) = reviewAction { community.reply(target, review.id, text).map { "" } }

        fun deleteReply(review: Review) = reviewAction { community.deleteReply(target, review.id).map { "" } }

        private fun reviewAction(block: suspend () -> Outcome<String>) {
            viewModelScope.launch {
                when (val result = block()) {
                    is Outcome.Success -> {
                        if (result.value.isNotBlank()) events.send(BookEvent.Message(result.value))
                        refreshReviews()
                    }

                    is Outcome.Failure -> {
                        events.send(BookEvent.Failed(result.error))
                    }
                }
            }
        }

        private val target get() = ReviewTarget.OfBook(bookId)

        fun setQuantity(quantity: Int) {
            val stock = local.value.detail?.book?.stock ?: return
            local.update { it.copy(quantity = quantity.coerceIn(1, stock.coerceAtLeast(1))) }
        }

        fun addToCart() {
            viewModelScope.launch {
                if (session.currentUser() == null) {
                    events.send(BookEvent.SignInRequired)
                    return@launch
                }
                local.update { it.copy(adding = true) }
                val result = cart.add(bookId, local.value.quantity)
                local.update { it.copy(adding = false) }
                events.send(if (result is Outcome.Failure) BookEvent.Failed(result.error) else BookEvent.AddedToCart)
            }
        }

        fun toggleWishlist() {
            viewModelScope.launch {
                if (session.currentUser() == null) {
                    events.send(BookEvent.SignInRequired)
                    return@launch
                }
                val result = if (uiState.value.inWishlist) wishlist.remove(bookId) else wishlist.add(bookId)
                if (result is Outcome.Failure) events.send(BookEvent.Failed(result.error))
            }
        }

        /** "Notify me when it is back", for a sold-out book, or withdrawing that. */
        fun toggleRequest() {
            viewModelScope.launch {
                if (session.currentUser() == null) {
                    events.send(BookEvent.SignInRequired)
                    return@launch
                }
                val wanted = local.value.requested != true
                when (val result = books.setRequested(bookId, wanted)) {
                    is Outcome.Success -> {
                        local.update { it.copy(requested = result.value) }
                        events.send(BookEvent.RequestChanged(result.value))
                    }

                    is Outcome.Failure -> {
                        events.send(BookEvent.Failed(result.error))
                    }
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
