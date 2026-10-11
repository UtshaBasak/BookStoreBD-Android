package io.github.utshabasak.bookstorebd.feature.wishlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.CartRepository
import io.github.utshabasak.bookstorebd.core.data.repository.SettingsRepository
import io.github.utshabasak.bookstorebd.core.data.repository.WishlistRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.ui.BookRow
import io.github.utshabasak.bookstorebd.core.ui.EmptyState
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CartRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CatalogueRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignInRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.WishlistRoute
import io.github.utshabasak.bookstorebd.core.ui.readableWidth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WishlistUiState(
    val signedIn: Boolean? = null,
    val books: List<Book> = emptyList(),
    val refreshing: Boolean = false,
    val sort: WishlistSort = WishlistSort.Recent,
)

/**
 * The orders a wishlist can be put in, as on the website. The list arrives in
 * the order the books were saved, and is short enough to sort here.
 */
enum class WishlistSort(
    val label: Int,
) {
    Recent(R.string.feature_wishlist_sort_recent),
    Oldest(R.string.feature_wishlist_sort_oldest),
    InStock(R.string.feature_wishlist_sort_in_stock),
    Deals(R.string.feature_wishlist_sort_deals),
    PriceLow(R.string.feature_wishlist_sort_price_low),
    PriceHigh(R.string.feature_wishlist_sort_price_high),
    Title(R.string.feature_wishlist_sort_title),
    ;

    fun apply(books: List<Book>): List<Book> =
        when (this) {
            Recent -> books.asReversed()
            Oldest -> books
            InStock -> books.sortedByDescending { it.inStock }
            Deals -> books.sortedByDescending { it.discountPercent }
            PriceLow -> books.sortedBy { it.salePrice }
            PriceHigh -> books.sortedByDescending { it.salePrice }
            Title -> books.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        }

    companion object {
        fun from(name: String?): WishlistSort = entries.firstOrNull { it.name == name } ?: Recent
    }
}

sealed interface WishlistEvent {
    data object MovedToCart : WishlistEvent

    data class Failed(
        val error: AppError,
    ) : WishlistEvent
}

@HiltViewModel
class WishlistViewModel
    @Inject
    constructor(
        private val wishlist: WishlistRepository,
        private val cart: CartRepository,
        private val settings: SettingsRepository,
        session: UserSession,
    ) : ViewModel() {
        private val refreshing = MutableStateFlow(false)
        private val events = Channel<WishlistEvent>(Channel.BUFFERED)
        val eventFlow: Flow<WishlistEvent> = events.receiveAsFlow()

        val uiState: StateFlow<WishlistUiState> =
            combine(session.user, wishlist.wishlist, refreshing, settings.wishlistSort) { user, books, busy, sortName ->
                val sort = WishlistSort.from(sortName)
                WishlistUiState(signedIn = user != null, books = sort.apply(books), refreshing = busy, sort = sort)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), WishlistUiState())

        fun refresh() {
            viewModelScope.launch {
                refreshing.value = true
                (wishlist.refresh() as? Outcome.Failure)?.let { events.send(WishlistEvent.Failed(it.error)) }
                refreshing.value = false
            }
        }

        fun setSort(sort: WishlistSort) {
            viewModelScope.launch { settings.setWishlistSort(sort.name) }
        }

        fun remove(book: Book) {
            viewModelScope.launch { (wishlist.remove(book.id) as? Outcome.Failure)?.let { events.send(WishlistEvent.Failed(it.error)) } }
        }

        /** Puts one copy in the cart and takes the book off the wishlist, as the website's "Move to cart" does. */
        fun moveToCart(book: Book) {
            viewModelScope.launch {
                when (val added = cart.add(book.id)) {
                    is Outcome.Failure -> {
                        events.send(WishlistEvent.Failed(added.error))
                    }

                    is Outcome.Success -> {
                        wishlist.remove(book.id)
                        events.send(WishlistEvent.MovedToCart)
                    }
                }
            }
        }

        private companion object {
            const val STOP_MS = 5_000L
        }
    }

fun EntryProviderScope<NavKey>.wishlistEntry(navigator: Navigator) {
    entry<WishlistRoute> { WishlistRouteScreen(navigator) }
}

@Composable
private fun WishlistRouteScreen(
    navigator: Navigator,
    viewModel: WishlistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val moved = stringResource(R.string.feature_wishlist_moved)
    val viewCart = stringResource(R.string.feature_wishlist_view_cart)
    var failure by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(state.signedIn) { if (state.signedIn == true) viewModel.refresh() }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                WishlistEvent.MovedToCart -> {
                    if (snackbar.showSnackbar(moved, viewCart) ==
                        SnackbarResult.ActionPerformed
                    ) {
                        navigator.navigate(CartRoute)
                    }
                }

                is WishlistEvent.Failed -> {
                    failure = event.error
                }
            }
        }
    }
    ErrorSnackbarEffect(failure, snackbar) { failure = null }
    WishlistScreen(
        state = state,
        snackbar = snackbar,
        onRefresh = viewModel::refresh,
        onSort = viewModel::setSort,
        onOpenBook = { navigator.navigate(BookRoute(it.id)) },
        onRemove = viewModel::remove,
        onMoveToCart = viewModel::moveToCart,
        onSignIn = { navigator.navigate(SignInRoute) },
        onBrowse = { navigator.navigate(CatalogueRoute()) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
    state: WishlistUiState,
    onRefresh: () -> Unit,
    onOpenBook: (Book) -> Unit,
    onRemove: (Book) -> Unit,
    onMoveToCart: (Book) -> Unit,
    onSignIn: () -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
    onSort: (WishlistSort) -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_wishlist_title)) },
                actions = { if (state.books.size > 1) SortMenu(state.sort, onSort) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val inner = Modifier.padding(padding)
        when {
            state.signedIn == false -> {
                EmptyState(
                    icon = Icons.Outlined.Lock,
                    title = stringResource(R.string.feature_wishlist_signed_out_title),
                    body = stringResource(R.string.feature_wishlist_signed_out_body),
                    actionLabel = stringResource(R.string.feature_wishlist_sign_in),
                    onAction = onSignIn,
                    modifier = inner,
                )
            }

            state.signedIn == true && state.books.isEmpty() && !state.refreshing -> {
                EmptyState(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = stringResource(R.string.feature_wishlist_empty_title),
                    body = stringResource(R.string.feature_wishlist_empty_body),
                    actionLabel = stringResource(R.string.feature_wishlist_browse),
                    onAction = onBrowse,
                    modifier = inner,
                )
            }

            else -> {
                PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = inner.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize().readableWidth()) {
                        items(state.books, key = { it.id }) { book ->
                            BookRow(
                                book = book,
                                onClick = { onOpenBook(book) },
                                trailing = {
                                    Row {
                                        if (book.inStock) {
                                            IconButton(onClick = { onMoveToCart(book) }) {
                                                Icon(
                                                    Icons.Outlined.AddShoppingCart,
                                                    contentDescription = stringResource(R.string.feature_wishlist_move, book.title),
                                                )
                                            }
                                        }
                                        IconButton(onClick = { onRemove(book) }) {
                                            Icon(
                                                Icons.Outlined.Delete,
                                                contentDescription = stringResource(R.string.feature_wishlist_remove, book.title),
                                            )
                                        }
                                    }
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenu(
    sort: WishlistSort,
    onSort: (WishlistSort) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = stringResource(R.string.feature_wishlist_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            WishlistSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label), fontWeight = if (option == sort) FontWeight.Bold else null) },
                    onClick = {
                        onSort(option)
                        open = false
                    },
                )
            }
        }
    }
}
