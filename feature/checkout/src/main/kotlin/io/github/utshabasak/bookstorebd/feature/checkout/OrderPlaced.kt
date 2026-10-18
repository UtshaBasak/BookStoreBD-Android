package io.github.utshabasak.bookstorebd.feature.checkout

import android.content.ClipData
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.OrderRepository
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.DeliveryRules
import io.github.utshabasak.bookstorebd.core.model.Order
import io.github.utshabasak.bookstorebd.core.ui.buildReceipt
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatDate
import io.github.utshabasak.bookstorebd.core.ui.formatTaka
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrderPlacedRoute
import io.github.utshabasak.bookstorebd.core.ui.readableWidth
import io.github.utshabasak.bookstorebd.core.ui.writeReceiptPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/** The order just placed, as the server has it: for the summary and the receipt. */
@HiltViewModel(assistedFactory = OrderPlacedViewModel.Factory::class)
class OrderPlacedViewModel
    @AssistedInject
    constructor(
        @Assisted orderNumber: String,
        orders: OrderRepository,
        config: ConfigRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(orderNumber: String): OrderPlacedViewModel
        }

        val order: StateFlow<Order?> = orders.order(orderNumber).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

        val delivery: StateFlow<DeliveryRules?> =
            config.config.map { it?.delivery }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

        init {
            viewModelScope.launch { orders.refreshOrder(orderNumber) }
        }

        private companion object {
            const val STOP_MS = 5_000L
        }
    }

/** What the confirmation can lead to. */
data class OrderPlacedActions(
    val onTrack: () -> Unit,
    val onContinue: () -> Unit,
    val onReceipt: (Order) -> Unit = {},
)

@Composable
internal fun OrderPlacedDestination(
    route: OrderPlacedRoute,
    onTrack: () -> Unit,
    onContinue: () -> Unit,
) {
    val viewModel = hiltViewModel<OrderPlacedViewModel, OrderPlacedViewModel.Factory>(creationCallback = { it.create(route.orderNumber) })
    val order by viewModel.order.collectAsStateWithLifecycle()
    val delivery by viewModel.delivery.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val saved = stringResource(R.string.feature_checkout_receipt_saved)
    val notSaved = stringResource(R.string.feature_checkout_receipt_failed)
    val saveReceipt =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF)) { uri ->
            val placed = order ?: return@rememberLauncherForActivityResult
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch { snackbar.showSnackbar(if (saveReceipt(context, placed, uri)) saved else notSaved) }
        }
    OrderPlacedScreen(
        route = route,
        order = order,
        delivery = delivery,
        snackbar = snackbar,
        actions =
            OrderPlacedActions(
                onTrack = onTrack,
                onContinue = onContinue,
                onReceipt = { saveReceipt.launch("BookStoreBD-order-${it.orderNumber}.pdf") },
            ),
    )
}

/**
 * The order is in: its number, what to pay on delivery and when it comes, the
 * receipt, the way to track it, and what was ordered, as on the website.
 */
@Composable
fun OrderPlacedScreen(
    route: OrderPlacedRoute,
    actions: OrderPlacedActions,
    modifier: Modifier = Modifier,
    order: Order? = null,
    delivery: DeliveryRules? = null,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CheckoutSteps(current = CheckoutStep.Done, modifier = Modifier.padding(bottom = 8.dp))
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = BookStoreTheme.colors.success,
                modifier = Modifier.size(72.dp),
            )
            Text(
                stringResource(R.string.feature_checkout_placed_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.feature_checkout_order_number, route.orderNumber), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("order", route.orderNumber))) }
            }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Text(stringResource(R.string.feature_checkout_copy_number), Modifier.padding(start = 8.dp))
            }
            // The order as the server placed it, once loaded: what is actually owed.
            val owed = order?.total ?: route.total
            Text(
                stringResource(R.string.feature_checkout_pay_on_delivery, formatTaka(owed, locale)) +
                    delivery?.let {
                        " " +
                            stringResource(
                                R.string.feature_checkout_delivery_days,
                                formatCount(it.daysInsideDhaka, locale),
                                formatCount(it.daysOutsideDhaka, locale),
                            )
                    }.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            route.promoMessage?.let { why ->
                val code = route.promoCode
                Text(
                    if (code.isNullOrBlank()) why else stringResource(R.string.feature_checkout_promo_not_applied, code, why),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BookStoreTheme.colors.warning,
                    textAlign = TextAlign.Center,
                )
            }
            if (route.unavailable.isNotEmpty()) {
                Text(
                    stringResource(R.string.feature_checkout_left_out, leftOutList(route.unavailable)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BookStoreTheme.colors.warning,
                    textAlign = TextAlign.Center,
                )
            }
            Button(onClick = actions.onTrack, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Outlined.LocalShipping, contentDescription = null)
                Text(stringResource(R.string.feature_checkout_track), Modifier.padding(start = 8.dp))
            }
            if (order != null) {
                OutlinedButton(onClick = { actions.onReceipt(order) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                    Text(stringResource(R.string.feature_checkout_receipt), Modifier.padding(start = 8.dp))
                }
            }
            TextButton(onClick = actions.onContinue) { Text(stringResource(R.string.feature_checkout_continue)) }
            order?.let { OrderSummary(it) }
        }
    }
}

/** What was ordered, what it cost, how it is paid and where it goes. */
@Composable
private fun OrderSummary(order: Order) {
    val locale = currentLocale()
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.feature_checkout_order_summary),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        order.lines.forEach { line ->
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                Column(Modifier.weight(1f)) {
                    Text(line.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.feature_checkout_line_quantity, line.author, formatCount(line.quantity, locale)),
                        style = MaterialTheme.typography.bodySmall,
                        color = BookStoreTheme.colors.textMuted,
                    )
                }
                Text(formatTaka(line.lineTotal, locale), style = MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider()
        SummaryRow(stringResource(R.string.feature_checkout_subtotal), formatTaka(order.booksTotal, locale))
        SummaryRow(
            stringResource(R.string.feature_checkout_delivery_charge),
            if (order.shipping > 0) formatTaka(order.shipping, locale) else stringResource(R.string.feature_checkout_free),
        )
        if (order.discount > 0) {
            val label =
                order.promo?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.feature_checkout_discount_code, it) }
                    ?: stringResource(R.string.feature_checkout_discount)
            SummaryRow(label, "− " + formatTaka(order.discount, locale))
        }
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
            Text(
                stringResource(R.string.feature_checkout_total),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatTaka(order.total, locale),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = BookStoreTheme.colors.price,
            )
        }
        val d = order.delivery
        if (d.paymentMethod.isNotBlank()) {
            Text(stringResource(R.string.feature_checkout_payment_method, d.paymentMethod), style = MaterialTheme.typography.bodyMedium)
        }
        val place = listOf(d.address, d.district, d.division).filter { it.isNotBlank() }.joinToString(", ")
        if (place.isNotBlank()) {
            Text(stringResource(R.string.feature_checkout_delivering_to, place), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Writes the receipt where the person chose, off the main thread; false if it could not be written. */
private suspend fun saveReceipt(
    context: Context,
    order: Order,
    uri: Uri,
): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val locale = context.resources.configuration.locales[0]
            val receipt =
                buildReceipt(
                    order,
                    context.resources,
                    locale,
                    formatDate(Instant.now().toString(), locale, withTime = true),
                    context.getString(R.string.feature_checkout_status_confirmed),
                )
            context.contentResolver.openOutputStream(uri)?.use { writeReceiptPdf(receipt, it) } ?: error("No output")
        }.isSuccess
    }

private const val PDF = "application/pdf"
