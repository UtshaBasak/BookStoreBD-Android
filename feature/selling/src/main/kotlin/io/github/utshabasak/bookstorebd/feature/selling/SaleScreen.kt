package io.github.utshabasak.bookstorebd.feature.selling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.Order
import io.github.utshabasak.bookstorebd.core.model.OrderLine
import io.github.utshabasak.bookstorebd.core.model.OrderStatus
import io.github.utshabasak.bookstorebd.core.model.Pricing
import io.github.utshabasak.bookstorebd.core.ui.ErrorState
import io.github.utshabasak.bookstorebd.core.ui.SkeletonBlock
import io.github.utshabasak.bookstorebd.core.ui.SkeletonScreen
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatDate
import io.github.utshabasak.bookstorebd.core.ui.formatTaka
import io.github.utshabasak.bookstorebd.core.ui.readableWidth

data class SaleActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onStatus: (OrderStatus) -> Unit,
    val onCancel: (String) -> Unit,
    val onSlip: (Order) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleScreen(
    orderNumber: String,
    state: SaleUiState,
    actions: SaleActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(orderNumber) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_selling_back))
                    }
                },
                actions = {
                    state.order?.let { order ->
                        IconButton(onClick = { actions.onSlip(order) }) {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = stringResource(R.string.feature_selling_slip_download))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val inner = Modifier.fillMaxSize().padding(padding)
        val order = state.order
        when {
            order == null && state.error != null -> {
                ErrorState(state.error, onRetry = actions.onRefresh, modifier = inner)
            }

            order == null -> {
                SkeletonScreen(inner) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(PLACEHOLDERS) { SkeletonBlock(Modifier.fillMaxWidth().heightIn(min = 72.dp)) }
                    }
                }
            }

            else -> {
                PullToRefreshBox(isRefreshing = state.loading, onRefresh = actions.onRefresh, modifier = inner) {
                    SaleDetails(order, state.busy, actions) { confirmCancel = true }
                }
            }
        }
    }
    if (confirmCancel) CancelDialog(onCancel = { reason -> actions.onCancel(reason) }, onDismiss = { confirmCancel = false })
}

@Composable
private fun SaleDetails(
    order: Order,
    busy: Boolean,
    actions: SaleActions,
    onAskCancel: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().readableWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { StatusCard(order, busy, actions.onStatus) }
        item { Heading(stringResource(R.string.feature_selling_your_books)) }
        items(order.lines, key = { it.id }) { line -> LineRow(line) }
        item { Totals(order) }
        item { BuyerCard(order) }
        if (order.canCancel) {
            item {
                OutlinedButton(onClick = onAskCancel, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.feature_selling_cancel), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

/** Where the order is, and the next steps the seller may take: up to Shipped, as on the website. */
@Composable
private fun StatusCard(
    order: Order,
    busy: Boolean,
    onStatus: (OrderStatus) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.feature_selling_placed_on, formatDate(order.placedAt, withTime = true)),
                style = MaterialTheme.typography.bodySmall,
                color = BookStoreTheme.colors.textMuted,
            )
            Text(statusText(order.status), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            val moves = order.statusOptions.filter { it != order.status }
            when {
                moves.isNotEmpty() -> {
                    Text(stringResource(R.string.feature_selling_move_to), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        moves.forEach { status ->
                            OutlinedButton(onClick = { onStatus(status) }, enabled = !busy) { Text(statusText(status)) }
                        }
                    }
                }

                order.status in AFTER_SELLER -> {
                    Text(stringResource(R.string.feature_selling_shop_delivers), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun LineRow(line: OrderLine) {
    val locale = currentLocale()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                line.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textDecoration = if (line.isCancelled) TextDecoration.LineThrough else null,
            )
            Text(
                stringResource(R.string.feature_selling_line_price, formatTaka(line.price, locale), formatCount(line.quantity, locale)),
                style = MaterialTheme.typography.bodySmall,
                color = BookStoreTheme.colors.textMuted,
            )
            if (line.isCancelled) {
                Text(
                    statusText(OrderStatus.Cancelled),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Text(formatTaka(line.lineTotal, locale), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun Totals(order: Order) {
    val locale = currentLocale()
    val books = order.booksTotal()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TotalRow(stringResource(order.booksLabel()), formatTaka(books, locale))
            TotalRow(
                stringResource(R.string.feature_selling_slip_fee, Pricing.SELLER_FEE_PERCENT),
                "- " + formatTaka(books - Pricing.sellerReceives(books), locale),
            )
            HorizontalDivider()
            TotalRow(
                stringResource(R.string.feature_selling_slip_receive),
                formatTaka(Pricing.sellerReceives(books), locale),
                strong = true,
            )
            Text(
                stringResource(R.string.feature_selling_paid_when),
                style = MaterialTheme.typography.bodySmall,
                color = BookStoreTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun TotalRow(
    label: String,
    value: String,
    strong: Boolean = false,
) {
    Row {
        Text(
            label,
            style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BuyerCard(order: Order) {
    val delivery = order.delivery
    val area = listOf(delivery.district, delivery.division).filter { it.isNotBlank() }.joinToString(", ")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Heading(stringResource(R.string.feature_selling_slip_buyer))
            if (delivery.name.isNotBlank()) Text(delivery.name, style = MaterialTheme.typography.bodyLarge)
            if (order.buyerEmail.isNotBlank()) Text(order.buyerEmail, style = MaterialTheme.typography.bodyMedium)
            if (area.isNotBlank()) Text(area, style = MaterialTheme.typography.bodyMedium, color = BookStoreTheme.colors.textMuted)
            if (delivery.note.isNotBlank()) {
                Text(stringResource(R.string.feature_selling_buyer_note, delivery.note), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CancelDialog(
    onCancel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_selling_cancel_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.feature_selling_cancel_body))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(MAX_REASON) },
                    label = { Text(stringResource(R.string.feature_selling_cancel_reason)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onCancel(reason)
                onDismiss()
            }) { Text(stringResource(R.string.feature_selling_cancel_confirm), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_selling_keep)) } },
    )
}

/** Past this point the shop's courier has the books, and the seller's part is done. */
private val AFTER_SELLER = setOf(OrderStatus.OutForDelivery, OrderStatus.Delivered)

private const val PLACEHOLDERS = 3
private const val MAX_REASON = 200
