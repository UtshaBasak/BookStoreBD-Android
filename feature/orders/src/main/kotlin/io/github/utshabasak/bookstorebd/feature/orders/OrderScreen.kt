package io.github.utshabasak.bookstorebd.feature.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.OrderRepository
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.CancelledBy
import io.github.utshabasak.bookstorebd.core.model.Order
import io.github.utshabasak.bookstorebd.core.model.OrderLine
import io.github.utshabasak.bookstorebd.core.model.OrderStatus
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.ui.BookCover
import io.github.utshabasak.bookstorebd.core.ui.BookRowSkeleton
import io.github.utshabasak.bookstorebd.core.ui.ErrorState
import io.github.utshabasak.bookstorebd.core.ui.SkeletonBlock
import io.github.utshabasak.bookstorebd.core.ui.SkeletonScreen
import io.github.utshabasak.bookstorebd.core.ui.conditionLabel
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatDate
import io.github.utshabasak.bookstorebd.core.ui.formatTaka
import io.github.utshabasak.bookstorebd.core.ui.readableWidth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrderUiState(
    val order: Order? = null,
    val refreshing: Boolean = false,
    val error: AppError? = null,
    val cancelling: Boolean = false,
)

sealed interface OrderEvent {
    data class Cancelled(
        val message: String,
    ) : OrderEvent

    data class Failed(
        val error: AppError,
    ) : OrderEvent
}

/** One order's tracking page: opens from the saved copy and refreshes. */
@HiltViewModel(assistedFactory = OrderViewModel.Factory::class)
class OrderViewModel
    @AssistedInject
    constructor(
        @Assisted private val orderNumber: String,
        private val orders: OrderRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(orderNumber: String): OrderViewModel
        }

        private val status = MutableStateFlow(OrderUiState())

        val uiState: StateFlow<OrderUiState> =
            combine(orders.order(orderNumber), status) { order, current -> current.copy(order = order) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), OrderUiState())

        private val events = Channel<OrderEvent>(Channel.BUFFERED)
        val eventFlow: Flow<OrderEvent> = events.receiveAsFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                status.update { it.copy(refreshing = true) }
                val result = orders.refreshOrder(orderNumber)
                status.update { it.copy(refreshing = false, error = (result as? Outcome.Failure)?.error) }
            }
        }

        fun cancel(reason: String) {
            viewModelScope.launch {
                status.update { it.copy(cancelling = true) }
                val result = orders.cancel(orderNumber, reason)
                status.update { it.copy(cancelling = false) }
                events.send(
                    when (result) {
                        is Outcome.Success -> OrderEvent.Cancelled(result.value)
                        is Outcome.Failure -> OrderEvent.Failed(result.error)
                    },
                )
            }
        }

        private companion object {
            const val STOP_MS = 5_000L
        }
    }

data class OrderActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onCopy: (String) -> Unit,
    val onCancel: (String) -> Unit,
    val onReturn: (Order, List<OrderLine>) -> Unit,
    val onOpenBook: (String) -> Unit,
    val onReceipt: (Order) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(
    orderNumber: String,
    state: OrderUiState,
    actions: OrderActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_orders_tracking_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_orders_back))
                    }
                },
                actions = {
                    state.order?.let { order ->
                        IconButton(onClick = { actions.onReceipt(order) }) {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = stringResource(R.string.feature_orders_receipt_download))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val order = state.order
        when {
            order == null && state.error != null -> {
                ErrorState(state.error, onRetry = actions.onRefresh, modifier = Modifier.padding(padding))
            }

            order == null -> {
                SkeletonScreen(Modifier.padding(padding)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SkeletonBlock(Modifier.fillMaxWidth().height(72.dp), RoundedCornerShape(12.dp))
                        repeat(2) { BookRowSkeleton() }
                        SkeletonBlock(Modifier.fillMaxWidth().height(120.dp), RoundedCornerShape(12.dp))
                    }
                }
            }

            else -> {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = actions.onRefresh,
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    OrderDetails(orderNumber, order, actions) { confirmCancel = true }
                }
            }
        }
    }
    if (confirmCancel || state.cancelling) {
        CancelDialog(busy = state.cancelling, onCancel = { reason ->
            actions.onCancel(reason)
            confirmCancel = false
        }, onDismiss = { confirmCancel = false })
    }
}

@Composable
private fun OrderDetails(
    orderNumber: String,
    order: Order,
    actions: OrderActions,
    onAskCancel: () -> Unit,
) {
    val locale = currentLocale()
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().readableWidth(),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.feature_orders_order_number),
                        style = MaterialTheme.typography.labelMedium,
                        color = BookStoreTheme.colors.textMuted,
                    )
                    Text(orderNumber, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text(
                        stringResource(R.string.feature_orders_placed_on, formatDate(order.placedAt, withTime = true)),
                        style = MaterialTheme.typography.bodySmall,
                        color = BookStoreTheme.colors.textMuted,
                    )
                }
                IconButton(onClick = { actions.onCopy(orderNumber) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.feature_orders_copy))
                }
            }
        }
        item {
            if (order.status == OrderStatus.Cancelled) CancelledNote(order) else Stepper(order.status)
        }
        if (order.status != OrderStatus.Cancelled && order.cancelledCount > 0) {
            item { PartlyCancelledNote(order) }
        }
        item {
            Text(
                stringResource(R.string.feature_orders_items),
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier.semantics {
                        heading()
                    },
            )
        }
        items(order.lines, key = { it.id }) { line -> LineRow(line) { actions.onOpenBook(line.bookId) } }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TotalRow(R.string.feature_orders_books_total, formatTaka(order.booksTotal, locale))
                    TotalRow(R.string.feature_orders_delivery, deliveryText(order.shipping))
                    if (order.discount > 0) TotalRow(R.string.feature_orders_discount, "−" + formatTaka(order.discount, locale))
                    HorizontalDivider()
                    TotalRow(R.string.feature_orders_total, formatTaka(order.total, locale), strong = true)
                    Text(
                        stringResource(paymentNote(order.status)),
                        style = MaterialTheme.typography.bodySmall,
                        color = BookStoreTheme.colors.textMuted,
                    )
                }
            }
        }
        item { DeliveryCard(order) }
        val returnable = order.returnable
        if (returnable.isNotEmpty()) {
            item {
                OutlinedButton(onClick = {
                    actions.onReturn(order, returnable)
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Replay, contentDescription = null)
                    Text(
                        stringResource(
                            if (returnable.size ==
                                1
                            ) {
                                R.string.feature_orders_return_book
                            } else {
                                R.string.feature_orders_return_books
                            },
                        ),
                        Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        if (order.canCancel) {
            item {
                OutlinedButton(
                    onClick = onAskCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.Cancel, contentDescription = null)
                    Text(stringResource(R.string.feature_orders_cancel_order), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** Order Confirmed → Processing → Shipped → Out for Delivery → Delivered, with the current step marked. */
@Composable
private fun Stepper(status: OrderStatus) {
    val current = OrderStatus.steps.indexOf(status)
    val spoken = stringResource(R.string.feature_orders_step_spoken, statusLabel(status))
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
        OrderStatus.steps.forEachIndexed { index, step ->
            val reached = index <= current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (reached) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                Text(
                    statusLabel(step),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (index == current) FontWeight.Bold else null,
                    color = if (reached) MaterialTheme.colorScheme.onSurface else BookStoreTheme.colors.textMuted,
                    modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                )
            }
            if (index < OrderStatus.steps.lastIndex) {
                Box(
                    Modifier
                        .padding(start = 13.dp)
                        .width(2.dp)
                        .height(14.dp)
                        .background(if (index < current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

@Composable
private fun CancelledNote(order: Order) {
    val line = order.lines.firstOrNull { it.isCancelled }
    val who =
        when (line?.cancelledBy) {
            CancelledBy.Buyer -> stringResource(R.string.feature_orders_cancelled_by_you)
            CancelledBy.Seller -> stringResource(R.string.feature_orders_cancelled_by_seller)
            CancelledBy.Admin -> stringResource(R.string.feature_orders_cancelled_by_shop)
            else -> stringResource(R.string.feature_orders_cancelled)
        }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(who, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp))
            }
            line?.cancelledAt?.let { Text(formatDate(it, withTime = true), style = MaterialTheme.typography.bodySmall) }
            line?.cancelReason?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.feature_orders_reason, it), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PartlyCancelledNote(order: Order) {
    Text(
        pluralStringResource(R.plurals.feature_orders_partly_cancelled, order.cancelledCount, order.cancelledCount, order.lines.size),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun LineRow(
    line: OrderLine,
    onOpen: () -> Unit,
) {
    val locale = currentLocale()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BookCover(url = line.coverUrl, drawnWidth = 56.dp, modifier = Modifier.width(56.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            TextButton(onClick = onOpen, contentPadding = PaddingValues(0.dp)) {
                Text(line.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(line.author, style = MaterialTheme.typography.bodySmall, color = BookStoreTheme.colors.textMuted)
            Text(
                stringResource(R.string.feature_orders_line_price, formatTaka(line.price, locale), line.quantity),
                style = MaterialTheme.typography.bodySmall,
            )
            // New or second-hand and in what condition, and who sells it, as the website's order table lists.
            Text(
                when {
                    line.type == BookType.New -> {
                        stringResource(R.string.feature_orders_line_new)
                    }

                    else -> {
                        line.condition?.let { stringResource(R.string.feature_orders_line_old, conditionLabel(it)) }
                            ?: stringResource(R.string.feature_orders_line_second_hand)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = BookStoreTheme.colors.textMuted,
            )
            if (line.sellerEmail.isNotBlank()) {
                Text(
                    stringResource(R.string.feature_orders_line_seller, line.sellerEmail),
                    style = MaterialTheme.typography.bodySmall,
                    color = BookStoreTheme.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                line.isCancelled -> {
                    Pill(
                        statusLabel(OrderStatus.Cancelled),
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                line.returnStatus != null -> {
                    Pill(
                        returnLabel(checkNotNull(line.returnStatus)),
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }

                else -> {}
            }
        }
        Text(formatTaka(line.lineTotal, locale), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun TotalRow(
    label: Int,
    value: String,
    strong: Boolean = false,
) {
    Row {
        Text(
            stringResource(label),
            style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (strong) BookStoreTheme.colors.price else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DeliveryCard(order: Order) {
    val delivery = order.delivery
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.feature_orders_delivery_to),
                style = MaterialTheme.typography.titleSmall,
                modifier =
                    Modifier.semantics {
                        heading()
                    },
            )
            Text(delivery.name, style = MaterialTheme.typography.bodyLarge)
            Text(delivery.phone, style = MaterialTheme.typography.bodyMedium)
            Text(delivery.address, style = MaterialTheme.typography.bodyMedium)
            Text(
                listOf(delivery.district, delivery.division).filter {
                    it.isNotBlank()
                }.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (delivery.note.isNotBlank()) {
                Text(
                    stringResource(R.string.feature_orders_note, delivery.note),
                    style = MaterialTheme.typography.bodySmall,
                    color = BookStoreTheme.colors.textMuted,
                )
            }
        }
    }
}

@Composable
internal fun CancelDialog(
    busy: Boolean,
    onCancel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.feature_orders_cancel_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.feature_orders_cancel_body))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(REASON_LIMIT) },
                    label = { Text(stringResource(R.string.feature_orders_cancel_reason)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCancel(reason) }, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.feature_orders_cancel_confirm))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.feature_orders_keep_order)) } },
    )
}

private const val REASON_LIMIT = 200

/** How the order is paid for, in the words that fit where it has got to. */
private fun paymentNote(status: OrderStatus): Int =
    when (status) {
        OrderStatus.Delivered -> R.string.feature_orders_paid_on_delivery
        OrderStatus.Cancelled -> R.string.feature_orders_nothing_to_pay
        else -> R.string.feature_orders_cash_on_delivery
    }
