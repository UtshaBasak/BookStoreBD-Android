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
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.CartItem
import io.github.utshabasak.bookstorebd.core.model.DeliveryRules
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.ui.BookRow
import io.github.utshabasak.bookstorebd.core.ui.EmptyState
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.QuantityStepper
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatTaka
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CartRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CatalogueRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CheckoutRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignInRoute
import io.github.utshabasak.bookstorebd.core.ui.readableWidth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
) {
    val booksTotal: Double get() = items.sumOf { it.lineTotal }
    val canCheckOut: Boolean get() = items.isNotEmpty() && items.all { it.book.inStock }
}

@HiltViewModel
class CartViewModel
    @Inject
    constructor(
        private val cart: CartRepository,
        session: UserSession,
        config: ConfigRepository,
    ) : ViewModel() {
        private val local = MutableStateFlow(CartUiState())

        val uiState: StateFlow<CartUiState> =
            combine(session.user, cart.cart, local, config.config) { user, items, state, settings ->
                state.copy(signedIn = user != null, items = items, delivery = settings?.delivery)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), CartUiState())

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
                            Text(
                                stringResource(R.string.feature_cart_books_total),
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
                        state.delivery?.let { rules ->
                            Text(
                                stringResource(
                                    R.string.feature_cart_delivery_note,
                                    formatTaka(rules.insideDhaka.toDouble(), locale),
                                    formatTaka(rules.outsideDhaka.toDouble(), locale),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = BookStoreTheme.colors.textMuted,
                            )
                        }
                        Button(
                            onClick = onCheckout,
                            enabled = state.canCheckOut,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) {
                            Text(stringResource(R.string.feature_cart_checkout))
                        }
                        if (!state.canCheckOut) {
                            Text(
                                stringResource(R.string.feature_cart_remove_sold_out),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
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
                        items(state.items, key = { it.book.id }) { item ->
                            CartLine(item, busy = state.busyBookId == item.book.id, onQuantity, onRemove, onOpenBook)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartLine(
    item: CartItem,
    busy: Boolean,
    onQuantity: (CartItem, Int) -> Unit,
    onRemove: (CartItem) -> Unit,
    onOpenBook: (String) -> Unit,
) {
    BookRow(
        book = item.book,
        onClick = { onOpenBook(item.book.id) },
        trailing = {
            IconButton(onClick = { onRemove(item) }, enabled = !busy) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.feature_cart_remove, item.book.title))
            }
        },
        below = {
            if (item.book.inStock) {
                QuantityStepper(
                    value = item.quantity,
                    max = item.book.stock,
                    onChange = { onQuantity(item, it) },
                    enabled = !busy,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Text(
                    stringResource(R.string.feature_cart_sold_out),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (item.adjusted) {
                Text(
                    stringResource(R.string.feature_cart_adjusted),
                    style = MaterialTheme.typography.bodySmall,
                    color = BookStoreTheme.colors.warning,
                )
            }
        },
    )
}
