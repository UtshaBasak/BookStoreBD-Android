package io.github.utshabasak.bookstorebd.feature.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.CartRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.RestockRequests
import io.github.utshabasak.bookstorebd.core.data.repository.WishlistRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.CartItem
import io.github.utshabasak.bookstorebd.core.model.DeliveryRules
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.ui.BookRow
import io.github.utshabasak.bookstorebd.core.ui.EmptyState
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.NotifyMeButton
import io.github.utshabasak.bookstorebd.core.ui.QuantityStepper
import io.github.utshabasak.bookstorebd.core.ui.ShopPerks
import io.github.utshabasak.bookstorebd.core.ui.WishlistButton
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatTaka
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CartRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CatalogueRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CheckoutRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignInRoute
import io.github.utshabasak.bookstorebd.core.ui.readableWidth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CartUiState(
    val signedIn: Boolean? = null,
    val items: List<CartItem> = emptyList(),
    val refreshing: Boolean = false,
    /** A book whose quantity is being changed, so its controls wait. */
    val busyBookId: String? = null,
    val error: AppError? = null,
    /** Delivery charges for the note under the total; null until the configuration is known. */
    val delivery: DeliveryRules? = null,
    val wishlist: Set<String> = emptySet(),
    /** Whether each sold-out book was asked for, by id. */
    val requested: Map<String, Boolean> = emptyMap(),
    /** The return window, for the shop's promises under the cart. */
    val returnDays: Int = 0,
    /** Who is signed in, so their own listing is not offered "Notify me". */
    val viewerEmail: String? = null,
) {
    /**
     * A sold-out book stays in the cart, so it can be bought when it is back,
     * but it is not part of what the cart costs, and checkout leaves it out.
     */
    val buyable: List<CartItem> get() = items.filter { it.book.inStock }
    val booksTotal: Double get() = buyable.sumOf { it.lineTotal }
    val canCheckOut: Boolean get() = buyable.isNotEmpty()
    val hasSoldOut: Boolean get() = buyable.size < items.size

    /** Copies ready for checkout, counted as the summary counts them. */
    val copies: Int get() = buyable.sumOf { it.quantity }
    val soldOutCount: Int get() = items.size - buyable.size

    fun isOwn(item: CartItem): Boolean = viewerEmail != null && item.book.sellerEmail.equals(viewerEmail, ignoreCase = true)
}

sealed interface CartEvent {
    data class RequestChanged(
        val requested: Boolean,
    ) : CartEvent
}

@HiltViewModel
class CartViewModel
    @Inject
    constructor(
        private val cart: CartRepository,
        session: UserSession,
        config: ConfigRepository,
        private val wishlist: WishlistRepository,
        private val restock: RestockRequests,
    ) : ViewModel() {
        private val local = MutableStateFlow(CartUiState())
        private val events = Channel<CartEvent>(Channel.BUFFERED)
        val eventFlow: Flow<CartEvent> = events.receiveAsFlow()

        val uiState: StateFlow<CartUiState> =
            combine(session.user, cart.cart, local, config.config, combine(wishlist.ids, restock.requested, ::Pair)) {
                user,
                items,
                state,
                settings,
                (saved, asked),
                ->
                state.copy(
                    signedIn = user != null,
                    viewerEmail = user?.email,
                    items = items,
                    delivery = settings?.delivery,
                    returnDays = settings?.returnWindowDays ?: 0,
                    wishlist = saved,
                    requested = asked,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), CartUiState())

        init {
            // "Notify me" knows, for each sold-out book, whether it was asked for.
            cart.cart
                .map { items -> items.filterNot { it.book.inStock }.map { it.book.id } }
                .distinctUntilChanged()
                .onEach { restock.load(it) }
                .launchIn(viewModelScope)
        }

        fun toggleWishlist(item: CartItem) =
            run(item.book.id) {
                if (item.book.id in uiState.value.wishlist) wishlist.remove(item.book.id) else wishlist.add(item.book.id)
            }

        fun toggleRequest(item: CartItem) {
            viewModelScope.launch {
                when (val result = restock.toggle(item.book.id)) {
                    is Outcome.Success -> events.send(CartEvent.RequestChanged(result.value))
                    is Outcome.Failure -> local.value = local.value.copy(error = result.error)
                }
            }
        }

        fun refresh() = run(null) { cart.refresh() }

        fun setQuantity(
            item: CartItem,
            quantity: Int,
        ) = run(item.book.id) { cart.setQuantity(item.book.id, quantity) }

        fun remove(item: CartItem) = run(item.book.id) { cart.remove(item.book.id) }

        fun errorShown() {
            local.value = local.value.copy(error = null)
        }

        private fun run(
            bookId: String?,
            call: suspend () -> Outcome<Unit>,
        ) {
            viewModelScope.launch {
                local.value = local.value.copy(refreshing = bookId == null, busyBookId = bookId)
                val result = call()
                local.value = local.value.copy(refreshing = false, busyBookId = null, error = (result as? Outcome.Failure)?.error)
            }
        }

        private companion object {
            const val STOP_MS = 5_000L
        }
    }

fun EntryProviderScope<NavKey>.cartEntry(navigator: Navigator) {
    entry<CartRoute> {
        val viewModel: CartViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.signedIn) { if (state.signedIn == true) viewModel.refresh() }
        val requested = stringResource(R.string.feature_cart_requested)
        val withdrawn = stringResource(R.string.feature_cart_request_withdrawn)
        LaunchedEffect(viewModel) {
            viewModel.eventFlow.collect { event ->
                when (event) {
                    is CartEvent.RequestChanged -> snackbar.showSnackbar(if (event.requested) requested else withdrawn)
                }
            }
        }
        ErrorSnackbarEffect(state.error, snackbar, viewModel::errorShown)
        CartScreen(
            state = state,
            snackbar = snackbar,
            onRefresh = viewModel::refresh,
            onQuantity = viewModel::setQuantity,
            onRemove = viewModel::remove,
            onOpenBook = { navigator.navigate(BookRoute(it)) },
            onCheckout = { navigator.navigate(CheckoutRoute) },
            onSignIn = { navigator.navigate(SignInRoute) },
            onBrowse = { navigator.navigate(CatalogueRoute()) },
            onToggleWishlist = viewModel::toggleWishlist,
            onToggleRequest = viewModel::toggleRequest,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    state: CartUiState,
    onRefresh: () -> Unit,
    onQuantity: (CartItem, Int) -> Unit,
    onRemove: (CartItem) -> Unit,
    onOpenBook: (String) -> Unit,
    onCheckout: () -> Unit,
    onSignIn: () -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onToggleWishlist: (CartItem) -> Unit = {},
    onToggleRequest: (CartItem) -> Unit = {},
) {
    val locale = currentLocale()
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_cart_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.items.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Delivery depends on the district, chosen at checkout: this total is before it.
                            Text(
                                stringResource(R.string.feature_cart_total_before_delivery),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                formatTaka(state.booksTotal, locale),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = BookStoreTheme.colors.price,
                            )
                        }
                        val deliveryNote =
                            state.delivery?.let { rules ->
                                stringResource(
                                    R.string.feature_cart_delivery_note,
                                    formatTaka(rules.insideDhaka.toDouble(), locale),
                                    formatTaka(rules.outsideDhaka.toDouble(), locale),
                                )
                            }
                        // The sold-out note stands on its own, whether or not the delivery charges are known yet.
                        val soldOutNote = stringResource(R.string.feature_cart_sold_out_not_included).takeIf { state.hasSoldOut }
                        listOfNotNull(deliveryNote, soldOutNote).joinToString(" ").takeIf { it.isNotEmpty() }?.let { note ->
                            Text(note, style = MaterialTheme.typography.bodySmall, color = BookStoreTheme.colors.textMuted)
                        }
                        Button(
                            onClick = onCheckout,
                            enabled = state.canCheckOut,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (state.canCheckOut) R.string.feature_cart_checkout else R.string.feature_cart_nothing_in_stock,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        val inner = Modifier.padding(padding)
        when {
            state.signedIn == false -> {
                EmptyState(
                    icon = Icons.Outlined.Lock,
                    title = stringResource(R.string.feature_cart_signed_out_title),
                    body = stringResource(R.string.feature_cart_signed_out_body),
                    actionLabel = stringResource(R.string.feature_cart_sign_in),
                    onAction = onSignIn,
                    modifier = inner,
                )
            }

            state.signedIn == true && state.items.isEmpty() && !state.refreshing -> {
                EmptyState(
                    icon = Icons.Outlined.ShoppingCart,
                    title = stringResource(R.string.feature_cart_empty_title),
                    body = stringResource(R.string.feature_cart_empty_body),
                    actionLabel = stringResource(R.string.feature_cart_browse),
                    onAction = onBrowse,
                    modifier = inner,
                )
            }

            else -> {
                PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = inner.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize().readableWidth()) {
                        if (state.items.isNotEmpty()) {
                            item(key = "waiting") {
                                // Copies, as the summary counts them; a sold-out book is named apart.
                                val ready =
                                    pluralStringResource(R.plurals.feature_cart_ready, state.copies, formatCount(state.copies, locale))
                                        .takeIf { state.copies > 0 }
                                val soldOut =
                                    pluralStringResource(
                                        R.plurals.feature_cart_sold_out_count,
                                        state.soldOutCount,
                                        formatCount(state.soldOutCount, locale),
                                    ).takeIf { state.soldOutCount > 0 }
                                Text(
                                    listOfNotNull(ready, soldOut).joinToString(", "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BookStoreTheme.colors.textMuted,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                        items(state.items, key = { it.book.id }) { item ->
                            CartLine(
                                item = item,
                                busy = state.busyBookId == item.book.id,
                                inWishlist = item.book.id in state.wishlist,
                                requested = state.requested[item.book.id],
                                own = state.isOwn(item),
                                actions = LineActions(onQuantity, onRemove, onOpenBook, onToggleWishlist, onToggleRequest),
                            )
                            HorizontalDivider()
                        }
                        if (state.items.isNotEmpty()) {
                            item(key = "more") {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                        Text(stringResource(R.string.feature_cart_continue))
                                    }
                                    ShopPerks(returnDays = state.returnDays, delivery = state.delivery, days = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** What a line of the cart can do. */
private class LineActions(
    val quantity: (CartItem, Int) -> Unit,
    val remove: (CartItem) -> Unit,
    val open: (String) -> Unit,
    val wishlist: (CartItem) -> Unit,
    val request: (CartItem) -> Unit,
)

@Composable
private fun CartLine(
    item: CartItem,
    busy: Boolean,
    inWishlist: Boolean,
    requested: Boolean?,
    own: Boolean,
    actions: LineActions,
) {
    val locale = currentLocale()
    BookRow(
        book = item.book,
        onClick = { actions.open(item.book.id) },
        trailing = {
            Column {
                WishlistButton(inWishlist = inWishlist, title = item.book.title, onToggle = { actions.wishlist(item) })
                IconButton(onClick = { actions.remove(item) }, enabled = !busy) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.feature_cart_remove, item.book.title))
                }
            }
        },
        below = {
            if (item.book.inStock) {
                QuantityStepper(
                    value = item.quantity,
                    max = item.book.stock,
                    onChange = { actions.quantity(item, it) },
                    enabled = !busy,
                    modifier = Modifier.padding(top = 8.dp),
                )
                when {
                    item.adjusted -> {
                        Text(
                            stringResource(R.string.feature_cart_adjusted),
                            style = MaterialTheme.typography.bodySmall,
                            color = BookStoreTheme.colors.warning,
                        )
                    }

                    item.book.stock <= LOW_STOCK -> {
                        Text(
                            pluralStringResource(R.plurals.feature_cart_only_left, item.book.stock, formatCount(item.book.stock, locale)),
                            style = MaterialTheme.typography.bodySmall,
                            color = BookStoreTheme.colors.warning,
                        )
                    }
                }
            } else {
                // It stays in the cart, out of the total, until it is back.
                Text(
                    stringResource(R.string.feature_cart_sold_out_stays),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                // Nobody to ask on one's own listing. A book whose check failed can still be asked for.
                if (!own) {
                    NotifyMeButton(
                        requested = requested == true,
                        onToggle = { actions.request(item) },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
    )
}

/** "Only N left" from this many copies down. */
private const val LOW_STOCK = 5
