package io.github.utshabasak.bookstorebd.feature.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.flatMap
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.OrderRepository
import io.github.utshabasak.bookstorebd.core.data.repository.PageLoadException
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Order
import io.github.utshabasak.bookstorebd.core.model.OrderLine
import io.github.utshabasak.bookstorebd.core.model.OrderQuery
import io.github.utshabasak.bookstorebd.core.model.OrderSort
import io.github.utshabasak.bookstorebd.core.model.OrderStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** One order line with the order it belongs to, for the Books tab. */
data class BoughtBook(
    val line: OrderLine,
    val order: Order,
)

data class OrdersUiState(
    val query: OrderQuery = OrderQuery(),
    /** The search box's text, applied to [query] a moment after typing stops. */
    val searchText: String = "",
)

/** The person's orders, with search, a status filter and sorting, as on the website's order list. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class OrdersViewModel
    @Inject
    constructor(
        repository: OrderRepository,
        session: UserSession,
    ) : ViewModel() {
        private val state = MutableStateFlow(OrdersUiState())
        val uiState: StateFlow<OrdersUiState> = state.asStateFlow()

        val signedIn: StateFlow<Boolean?> = session.user.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private val query =
            state
                .map { it.query }
                .distinctUntilChanged()
                .debounce { if (it.search.isEmpty()) 0L else SEARCH_DELAY_MS }

        val orders: Flow<PagingData<Order>> = query.flatMapLatest(repository::orders).cachedIn(viewModelScope)

        /** Every book bought, newest order first, each with its own return state. */
        val books: Flow<PagingData<BoughtBook>> = orders.map { page -> page.flatMap { order -> order.lines.map { BoughtBook(it, order) } } }

        fun setSearch(text: String) = state.update { it.copy(searchText = text, query = it.query.copy(search = text.trim())) }

        fun setStatus(status: OrderStatus?) = state.update { it.copy(query = it.query.copy(status = status)) }

        fun setSort(sort: OrderSort) = state.update { it.copy(query = it.query.copy(sort = sort)) }

        private companion object {
            const val SEARCH_DELAY_MS = 350L
        }
    }

/** The reason a page did not load, for the error state. */
internal fun Throwable.appError(): AppError = (this as? PageLoadException)?.error ?: AppError.Unexpected(this)
