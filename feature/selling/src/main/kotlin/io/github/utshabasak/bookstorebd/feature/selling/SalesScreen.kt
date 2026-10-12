package io.github.utshabasak.bookstorebd.feature.selling

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.OrderSort
import io.github.utshabasak.bookstorebd.core.model.OrderStatus
import io.github.utshabasak.bookstorebd.core.model.PayoutState
import io.github.utshabasak.bookstorebd.core.model.Pricing
import io.github.utshabasak.bookstorebd.core.model.Sale
import io.github.utshabasak.bookstorebd.core.ui.BookRowSkeleton
import io.github.utshabasak.bookstorebd.core.ui.EmptyState
import io.github.utshabasak.bookstorebd.core.ui.ErrorState
import io.github.utshabasak.bookstorebd.core.ui.LocalOpenBeside
import io.github.utshabasak.bookstorebd.core.ui.SkeletonScreen
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatDate
import io.github.utshabasak.bookstorebd.core.ui.formatTaka
import io.github.utshabasak.bookstorebd.core.ui.navigation.SaleRoute
import io.github.utshabasak.bookstorebd.core.ui.readableWidth

data class SalesActions(
    val onBack: () -> Unit,
    val onSearch: (String) -> Unit,
    val onStatus: (OrderStatus?) -> Unit,
    val onSort: (OrderSort) -> Unit,
    val onOpen: (String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    state: SalesUiState,
    sales: LazyPagingItems<Sale>,
    actions: SalesActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_selling_sales)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_selling_back))
                    }
                },
                actions = { SortMenu(state.query.sort, actions.onSort) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.searchText,
                onValueChange = actions.onSearch,
                placeholder = { Text(stringResource(R.string.feature_selling_search_sales)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            StatusChips(state.query.status, actions.onStatus)
            SalesList(sales, actions.onOpen)
        }
    }
}

@Composable
private fun StatusChips(
    selected: OrderStatus?,
    onStatus: (OrderStatus?) -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onStatus(null) },
                label = { Text(stringResource(R.string.feature_selling_all)) },
            )
        }
        OrderStatus.entries.forEach { status ->
            item { FilterChip(selected = selected == status, onClick = { onStatus(status) }, label = { Text(statusText(status)) }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SalesList(
    sales: LazyPagingItems<Sale>,
    onOpen: (String) -> Unit,
) {
    val refresh = sales.loadState.refresh
    when {
        refresh is LoadState.Loading && sales.itemCount == 0 -> {
            SkeletonScreen { Column { repeat(PLACEHOLDERS) { BookRowSkeleton() } } }
        }

        refresh is LoadState.Error && sales.itemCount == 0 -> {
            ErrorState(refresh.error.appError(), onRetry = sales::retry)
        }

        refresh is LoadState.NotLoading && sales.itemCount == 0 -> {
            EmptyState(
                icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                title = stringResource(R.string.feature_selling_no_sales),
                body = stringResource(R.string.feature_selling_no_sales_body),
            )
        }

        else -> {
            PullToRefreshBox(isRefreshing = refresh is LoadState.Loading, onRefresh = sales::refresh, modifier = Modifier.fillMaxSize()) {
                val openBeside = LocalOpenBeside.current
                LazyColumn(
                    Modifier.fillMaxSize().readableWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(count = sales.itemCount, key = sales.itemKey { it.line.id }) { index ->
                        val sale = sales[index] ?: return@items
                        // Lines of one order arrive together; the order's heading shows once.
                        val first = index == 0 || sales.peek(index - 1)?.line?.orderNumber != sale.line.orderNumber
                        val open = (openBeside as? SaleRoute)?.orderNumber == sale.line.orderNumber
                        SaleCard(sale, first, open) { onOpen(sale.line.orderNumber) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaleCard(
    sale: Sale,
    showOrder: Boolean,
    open: Boolean,
    onClick: () -> Unit,
) {
    val locale = currentLocale()
    val line = sale.line
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).semantics { selected = open },
        border = if (open) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        // On a wide screen, the order open beside the list.
        colors =
            CardDefaults.cardColors(
                containerColor = if (open) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (showOrder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(line.orderNumber, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(statusText(line.status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    stringResource(R.string.feature_selling_placed_by, formatDate(line.createdAt), sale.buyerEmail),
                    style = MaterialTheme.typography.bodySmall,
                    color = BookStoreTheme.colors.textMuted,
                )
                if (sale.buyerNote.isNotBlank()) {
                    Text(stringResource(R.string.feature_selling_buyer_note, sale.buyerNote), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                line.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.feature_selling_line_price, formatTaka(line.price, locale), formatCount(line.quantity, locale)),
                style = MaterialTheme.typography.bodySmall,
                color = BookStoreTheme.colors.textMuted,
            )
            if (!line.isCancelled && sale.payout != PayoutState.Returned) {
                Text(
                    stringResource(R.string.feature_selling_receive, formatTaka(Pricing.sellerReceives(line.lineTotal), locale)),
                    style = MaterialTheme.typography.titleSmall,
                    color = BookStoreTheme.colors.success,
                )
            }
            Box(Modifier.fillMaxWidth()) {
                Text(payoutText(sale), style = MaterialTheme.typography.labelMedium, color = BookStoreTheme.colors.textMuted)
            }
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
            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = stringResource(R.string.feature_selling_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            OrderSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                when (option) {
                                    OrderSort.Newest -> R.string.feature_selling_sort_newest
                                    OrderSort.Oldest -> R.string.feature_selling_sort_oldest
                                    OrderSort.TotalHigh -> R.string.feature_selling_sort_high
                                    OrderSort.TotalLow -> R.string.feature_selling_sort_low
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

private const val PLACEHOLDERS = 6
