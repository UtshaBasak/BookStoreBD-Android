package io.github.utshabasak.bookstorebd.feature.selling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.PageLoadException
import io.github.utshabasak.bookstorebd.core.data.repository.SellerRepository
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Discount
import io.github.utshabasak.bookstorebd.core.model.Listing
import io.github.utshabasak.bookstorebd.core.model.Order
import io.github.utshabasak.bookstorebd.core.model.OrderSort
import io.github.utshabasak.bookstorebd.core.model.OrderStatus
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.Sale
import io.github.utshabasak.bookstorebd.core.model.SalesQuery
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
import javax.inject.Inject

data class MyBooksUiState(
    val listings: List<Listing>? = null,
    val search: String = "",
    val refreshing: Boolean = false,
    val error: AppError? = null,
    /** The listing being saved, so its sheet shows progress. */
    val saving: String? = null,
) {
    val shown: List<Listing>
        get() =
            listings.orEmpty().filter { listing ->
                search.isBlank() ||
                    listing.book.title.contains(search.trim(), ignoreCase = true) ||
                    listing.book.author.contains(search.trim(), ignoreCase = true)
            }
}

/** A change to one listing; null fields stay as they are. */
data class ListingChange(
    val price: Int? = null,
    val stock: Int? = null,
    val discount: Discount? = null,
)

sealed interface MyBooksEvent {
    data class Saved(
        val title: String,
    ) : MyBooksEvent

    /** Some of the changes were refused; what was refused is in [error]. */
    data class NotSaved(
        val error: AppError,
    ) : MyBooksEvent

    data class Deleted(
        val message: String,
    ) : MyBooksEvent

    data class Failed(
        val error: AppError,
    ) : MyBooksEvent
}

@HiltViewModel
class MyBooksViewModel
    @Inject
    constructor(
        private val sellers: SellerRepository,
    ) : ViewModel() {
        private val state = MutableStateFlow(MyBooksUiState())
        val uiState: StateFlow<MyBooksUiState> = state.asStateFlow()

        private val events = Channel<MyBooksEvent>(Channel.BUFFERED)
        val eventFlow: Flow<MyBooksEvent> = events.receiveAsFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                state.update { it.copy(refreshing = true) }
                when (val result = sellers.listings()) {
                    is Outcome.Success -> state.update { it.copy(listings = result.value, error = null, refreshing = false) }
                    is Outcome.Failure -> state.update { it.copy(error = result.error, refreshing = false) }
                }
            }
        }

        fun setSearch(text: String) = state.update { it.copy(search = text) }

        /**
         * Saves each change on its own and checks every answer: what the
         * server refused is reported, rather than the whole being called saved.
         */
        fun save(
            listing: Listing,
            change: ListingChange,
        ) {
            viewModelScope.launch {
                state.update { it.copy(saving = listing.book.id) }
                val id = listing.book.id
                val results =
                    listOfNotNull(
                        change.price?.let { sellers.setPrice(id, it) },
                        change.stock?.let { sellers.setStock(id, it) },
                        change.discount?.let { sellers.setDiscount(id, it) },
                    )
                state.update { it.copy(saving = null) }
                val refused = results.filterIsInstance<Outcome.Failure>().firstOrNull()
                events.send(if (refused == null) MyBooksEvent.Saved(listing.book.title) else MyBooksEvent.NotSaved(refused.error))
                refresh()
            }
        }

        fun delete(listing: Listing) {
            viewModelScope.launch {
                when (val result = sellers.delete(listing.book.id)) {
                    is Outcome.Success -> {
                        state.update { current -> current.copy(listings = current.listings?.filterNot { it.book.id == listing.book.id }) }
                        events.send(MyBooksEvent.Deleted(result.value))
                    }

                    is Outcome.Failure -> {
                        events.send(MyBooksEvent.Failed(result.error))
                    }
                }
            }
        }
    }

data class SalesUiState(
    val query: SalesQuery = SalesQuery(),
    val searchText: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SalesViewModel
    @Inject
    constructor(
        private val sellers: SellerRepository,
    ) : ViewModel() {
        private val state = MutableStateFlow(SalesUiState())
        val uiState: StateFlow<SalesUiState> = state.asStateFlow()

        private val query = MutableStateFlow(SalesQuery())

        val sales: Flow<PagingData<Sale>> =
            query
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .flatMapLatest { sellers.sales(it) }
                .cachedIn(viewModelScope)

        fun setSearch(text: String) = change { it.copy(search = text) }.also { state.update { s -> s.copy(searchText = text) } }

        fun setStatus(status: OrderStatus?) = change { it.copy(status = status) }

        fun setSort(sort: OrderSort) = change { it.copy(sort = sort) }

        private fun change(update: (SalesQuery) -> SalesQuery) {
            val next = update(query.value)
            query.value = next
            state.update { it.copy(query = next) }
        }

        private companion object {
            /** A pause in typing, so each letter is not a request; filters wait the same short moment. */
            const val SEARCH_DEBOUNCE_MS = 300L
        }
    }

data class SaleUiState(
    val order: Order? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: AppError? = null,
)

sealed interface SaleEvent {
    data class Message(
        val text: String,
    ) : SaleEvent

    data object StatusChanged : SaleEvent

    data class Failed(
        val error: AppError,
    ) : SaleEvent
}

@HiltViewModel(assistedFactory = SaleViewModel.Factory::class)
class SaleViewModel
    @AssistedInject
    constructor(
        @Assisted private val orderNumber: String,
        private val sellers: SellerRepository,
        config: ConfigRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(orderNumber: String): SaleViewModel
        }

        /** How long after delivery a buyer may return a book, for the slip's payout note. */
        val returnWindowDays: StateFlow<Int> =
            config.config.map { it?.returnWindowDays ?: DEFAULT_RETURN_DAYS }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                DEFAULT_RETURN_DAYS,
            )

        private val state = MutableStateFlow(SaleUiState())
        val uiState: StateFlow<SaleUiState> = state.asStateFlow()

        private val events = Channel<SaleEvent>(Channel.BUFFERED)
        val eventFlow: Flow<SaleEvent> = events.receiveAsFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                state.update { it.copy(loading = true) }
                when (val result = sellers.order(orderNumber)) {
                    is Outcome.Success -> state.update { it.copy(order = result.value, loading = false, error = null) }
                    is Outcome.Failure -> state.update { it.copy(loading = false, error = result.error) }
                }
            }
        }

        fun setStatus(status: OrderStatus) =
            act {
                sellers.setStatus(orderNumber, status).also { if (it is Outcome.Success) events.send(SaleEvent.StatusChanged) }
            }

        fun cancel(reason: String) =
            act {
                sellers.cancel(orderNumber, reason).also { if (it is Outcome.Success) events.send(SaleEvent.Message(it.value)) }
            }

        private fun act(block: suspend () -> Outcome<*>) {
            if (state.value.busy) return
            viewModelScope.launch {
                state.update { it.copy(busy = true) }
                val result = block()
                state.update { it.copy(busy = false) }
                if (result is Outcome.Failure) events.send(SaleEvent.Failed(result.error))
                refresh()
            }
        }

        private companion object {
            const val DEFAULT_RETURN_DAYS = 7
        }
    }

internal fun Throwable.appError(): AppError = (this as? PageLoadException)?.error ?: AppError.Unexpected(this)
