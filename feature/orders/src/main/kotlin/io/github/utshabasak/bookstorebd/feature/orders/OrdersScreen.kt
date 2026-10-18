package io.github.utshabasak.bookstorebd.feature.orders

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.Order
import io.github.utshabasak.bookstorebd.core.model.OrderLine
import io.github.utshabasak.bookstorebd.core.model.OrderSort
import io.github.utshabasak.bookstorebd.core.model.OrderStatus
import io.github.utshabasak.bookstorebd.core.ui.BookCover
import io.github.utshabasak.bookstorebd.core.ui.BookRowSkeleton
import io.github.utshabasak.bookstorebd.core.ui.EmptyState
import io.github.utshabasak.bookstorebd.core.ui.ErrorState
import io.github.utshabasak.bookstorebd.core.ui.LocalOpenBeside
import io.github.utshabasak.bookstorebd.core.ui.SkeletonScreen
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatDate
import io.github.utshabasak.bookstorebd.core.ui.formatTaka
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrderRoute
import io.github.utshabasak.bookstorebd.core.ui.readableWidth

data class OrdersActions(
    val onBack: () -> Unit,
    val onSearch: (String) -> Unit,
    val onStatus: (OrderStatus?) -> Unit,
    val onSort: (OrderSort) -> Unit,
    val onOpenOrder: (String) -> Unit,
    val onOpenBook: (String) -> Unit,
    val onReturn: (BoughtBook) -> Unit,
    val onReview: (BoughtBook) -> Unit,
    val onShop: () -> Unit,
    val onCopy: (String) -> Unit = {},
    val onCancel: (String, String) -> Unit = { _, _ -> },
    val onReturnLines: (Order, List<OrderLine>) -> Unit = { _, _ -> },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    state: OrdersUiState,
    orders: LazyPagingItems<Order>,
    books: LazyPagingItems<BoughtBook>,
    initialBooksTab: Boolean,
    actions: OrdersActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var booksTab by rememberSaveable { mutableStateOf(initialBooksTab) }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_orders_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_orders_back))
                    }
                },
                actions = { SortMenu(state.query.sort, actions.onSort) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = if (booksTab) 1 else 0) {
                Tab(selected = !booksTab, onClick = {
                    booksTab = false
                }, text = { Text(stringResource(R.string.feature_orders_tab_orders)) })
                Tab(selected = booksTab, onClick = { booksTab = true }, text = { Text(stringResource(R.string.feature_orders_tab_books)) })
            }
            OutlinedTextField(
                value = state.searchText,
                onValueChange = actions.onSearch,
                placeholder = { Text(stringResource(R.string.feature_orders_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            StatusFilter(state.query.status, actions.onStatus)
            val refresh = orders.loadState.refresh
            PullToRefreshBox(
                isRefreshing = refresh is LoadState.Loading && orders.itemCount > 0,
                onRefresh = { orders.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                OrdersContent(state, orders, books, booksTab, actions)
            }
        }
    }
}

@Composable
private fun OrdersContent(
    state: OrdersUiState,
    orders: LazyPagingItems<Order>,
    books: LazyPagingItems<BoughtBook>,
    booksTab: Boolean,
    actions: OrdersActions,
) {
    val refresh = orders.loadState.refresh
    when {
        refresh is LoadState.Loading && orders.itemCount == 0 -> {
            SkeletonScreen { Column { repeat(LIST_PLACEHOLDERS) { BookRowSkeleton() } } }
        }

        refresh is LoadState.Error && orders.itemCount == 0 -> {
            ErrorState(refresh.error.appError(), onRetry = orders::retry)
        }

        refresh is LoadState.NotLoading && orders.itemCount == 0 -> {
            val filtered = state.query.search.isNotEmpty() || state.query.status != null
            EmptyState(
                icon = Icons.AutoMirrored.Outlined.ListAlt,
                title = stringResource(if (filtered) R.string.feature_orders_none_match else R.string.feature_orders_none),
                body =
                    stringResource(
                        if (filtered) R.string.feature_orders_none_match_body else R.string.feature_orders_none_body,
                    ),
                actionLabel = if (filtered) null else stringResource(R.string.feature_orders_start_shopping),
                onAction = if (filtered) null else actions.onShop,
            )
        }

        booksTab -> {
            BooksList(books, actions)
        }

        else -> {
            OrdersList(orders, state.cancelling, actions)
        }
    }
}

@Composable
private fun StatusFilter(
    selected: OrderStatus?,
    onSelect: (OrderStatus?) -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(selected = selected == null, onClick = {
                onSelect(null)
            }, label = { Text(stringResource(R.string.feature_orders_all)) })
        }
        items(OrderStatus.entries) { status ->
            FilterChip(selected = selected == status, onClick = {
                onSelect(
                    if (selected ==
                        status
                    ) {
                        null
                    } else {
                        status
                    },
                )
            }, label = { Text(statusLabel(status)) })
        }
    }
}

@Composable
private fun SortMenu(
    sort: OrderSort,
    onSort: (OrderSort) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = stringResource(R.string.feature_orders_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            OrderSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                when (option) {
                                    OrderSort.Newest -> R.string.feature_orders_sort_newest
                                    OrderSort.Oldest -> R.string.feature_orders_sort_oldest
                                    OrderSort.TotalHigh -> R.string.feature_orders_sort_high
                                    OrderSort.TotalLow -> R.string.feature_orders_sort_low
                                },
                            ),
                            fontWeight = if (option == sort) FontWeight.Bold else null,
                        )
                    },
                    onClick = {
                        onSort(option)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun OrdersList(
    orders: LazyPagingItems<Order>,
    cancelling: String?,
    actions: OrdersActions,
) {
    val openBeside = LocalOpenBeside.current
    var askCancel by rememberSaveable { mutableStateOf<String?>(null) }
    val asked = askCancel
    if (asked != null || cancelling != null) {
        CancelDialog(busy = cancelling != null, onCancel = { reason ->
            asked?.let { actions.onCancel(it, reason) }
            askCancel = null
        }, onDismiss = { askCancel = null })
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().readableWidth(),
    ) {
        items(count = orders.itemCount, key = orders.itemKey { it.orderNumber }) { index ->
            orders[index]?.let { order ->
                val open = (openBeside as? OrderRoute)?.orderNumber == order.orderNumber
                OrderCard(order, open, actions, onAskCancel = { askCancel = order.orderNumber })
            }
        }
        if (orders.loadState.append is LoadState.Loading) item { Loading() }
    }
}

@Composable
private fun OrderCard(
    order: Order,
    open: Boolean,
    actions: OrdersActions,
    onAskCancel: () -> Unit,
) {
    val locale = currentLocale()
    ElevatedCard(
        onClick = { actions.onOpenOrder(order.orderNumber) },
        // On a wide screen, the order open beside the list.
        colors =
            if (open) {
                CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            } else {
                CardDefaults.elevatedCardColors()
            },
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (open) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CardDefaults.elevatedShape) else Modifier)
                .semantics { selected = open },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(order.orderNumber, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    Text(
                        stringResource(R.string.feature_orders_placed_on, formatDate(order.placedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = BookStoreTheme.colors.textMuted,
                    )
                }
                IconButton(onClick = { actions.onCopy(order.orderNumber) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.feature_orders_copy))
                }
                StatusPill(order.status)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                order.lines.take(COVERS_SHOWN).forEach { line ->
                    BookCover(url = line.coverUrl, drawnWidth = 48.dp, modifier = Modifier.width(48.dp))
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        order.lines.joinToString(" · ") { it.title },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val count = order.lines.sumOf { it.quantity }
                    Text(
                        pluralStringResource(R.plurals.feature_orders_books, count, formatCount(count, locale)),
                        style = MaterialTheme.typography.bodySmall,
                        color = BookStoreTheme.colors.textMuted,
                    )
                }
            }
            CardTotals(order)
            CardActions(order, actions, onAskCancel)
        }
    }
}

/** Books, delivery and any discount, then what the order comes to, as under each order on the website's list. */
@Composable
private fun CardTotals(order: Order) {
    val locale = currentLocale()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CardTotal(stringResource(R.string.feature_orders_books_total), formatTaka(order.booksTotal, locale))
        CardTotal(stringResource(R.string.feature_orders_delivery), deliveryText(order.shipping))
        if (order.discount > 0) CardTotal(stringResource(R.string.feature_orders_discount), "−" + formatTaka(order.discount, locale))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.feature_orders_total), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(formatTaka(order.total, locale), style = MaterialTheme.typography.titleMedium, color = BookStoreTheme.colors.price)
        }
    }
}

@Composable
private fun CardTotal(
    label: String,
    value: String,
) {
    Row {
        Text(label, style = MaterialTheme.typography.bodySmall, color = BookStoreTheme.colors.textMuted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Return and Cancel from the list, as the website offers them: a return for
 * the books still returnable (the whole order, some of it, or the one book),
 * and a cancel while every book is still only confirmed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardActions(
    order: Order,
    actions: OrdersActions,
    onAskCancel: () -> Unit,
) {
    val live = order.lines.filterNot { it.isCancelled }
    val returnable = order.returnable
    // The server's rule, for the whole order, whatever a search matched.
    val canCancel = order.canCancel
    if (returnable.isEmpty() && !canCancel) return
    val locale = currentLocale()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (returnable.isNotEmpty()) {
            val label =
                when {
                    returnable.size > 1 && returnable.size == live.size -> {
                        stringResource(R.string.feature_orders_return_whole)
                    }

                    returnable.size > 1 -> {
                        pluralStringResource(
                            R.plurals.feature_orders_return_these,
                            returnable.size,
                            formatCount(returnable.size, locale),
                        )
                    }

                    live.size > 1 -> {
                        stringResource(R.string.feature_orders_return_book)
                    }

                    else -> {
                        stringResource(R.string.feature_orders_return_it)
                    }
                }
            OutlinedButton(onClick = { actions.onReturnLines(order, returnable) }) {
                Icon(Icons.Outlined.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(label, Modifier.padding(start = 8.dp))
            }
        }
        if (canCancel) {
            OutlinedButton(
                onClick = onAskCancel,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Outlined.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.feature_orders_cancel_order), Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun BooksList(
    books: LazyPagingItems<BoughtBook>,
    actions: OrdersActions,
) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.fillMaxSize().readableWidth()) {
        items(count = books.itemCount, key = books.itemKey { it.line.id }) { index ->
            books[index]?.let { BoughtBookRow(it, actions) }
        }
        if (books.loadState.append is LoadState.Loading) item { Loading() }
    }
}

@Composable
private fun BoughtBookRow(
    item: BoughtBook,
    actions: OrdersActions,
) {
    val line = item.line
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        BookCover(url = line.coverUrl, drawnWidth = 64.dp, modifier = Modifier.width(64.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { actions.onOpenBook(line.bookId) }, contentPadding = PaddingValues(0.dp)) {
                Text(line.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(line.author, style = MaterialTheme.typography.bodySmall, color = BookStoreTheme.colors.textMuted)
            Text(
                stringResource(R.string.feature_orders_from_order, line.orderNumber),
                style = MaterialTheme.typography.bodySmall,
                color = BookStoreTheme.colors.textMuted,
            )
            ReturnState(item, actions)
        }
    }
}

/** What can be done about a book bought, in the website's words: return it, its return's state, or why not. */
@Composable
private fun ReturnState(
    item: BoughtBook,
    actions: OrdersActions,
) {
    val line = item.line
    val status = line.returnStatus
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            line.isCancelled -> {
                Pill(
                    stringResource(R.string.feature_orders_status_cancelled),
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            status != null -> {
                Pill(returnLabel(status), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            }

            line.returnableUntil != null -> {
                OutlinedButton(onClick = { actions.onReturn(item) }, modifier = Modifier.heightIn(min = 40.dp)) {
                    Text(stringResource(R.string.feature_orders_return))
                }
                Text(
                    stringResource(R.string.feature_orders_until, formatDate(line.returnableUntil)),
                    style = MaterialTheme.typography.bodySmall,
                    color = BookStoreTheme.colors.textMuted,
                )
            }

            line.status == OrderStatus.Delivered -> {
                Text(
                    stringResource(R.string.feature_orders_return_over),
                    style = MaterialTheme.typography.bodySmall,
                    color = BookStoreTheme.colors.textMuted,
                )
            }

            else -> {
                Text(
                    stringResource(R.string.feature_orders_return_on_delivery),
                    style = MaterialTheme.typography.bodySmall,
                    color = BookStoreTheme.colors.textMuted,
                )
            }
        }
        // A review needs the book delivered, as the shop checks.
        if (line.status == OrderStatus.Delivered) {
            TextButton(onClick = { actions.onReview(item) }) { Text(stringResource(R.string.feature_orders_review)) }
        }
    }
}

@Composable
private fun Loading() {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
}

private const val LIST_PLACEHOLDERS = 5
private const val COVERS_SHOWN = 3
