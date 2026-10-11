package io.github.utshabasak.bookstorebd.feature.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.OrderStatus
import io.github.utshabasak.bookstorebd.core.model.ReturnStatus

@Composable
internal fun statusLabel(status: OrderStatus): String = stringResource(statusRes(status))

internal fun statusRes(status: OrderStatus): Int =
    when (status) {
        OrderStatus.Confirmed -> R.string.feature_orders_status_confirmed
        OrderStatus.Processing -> R.string.feature_orders_status_processing
        OrderStatus.Shipped -> R.string.feature_orders_status_shipped
        OrderStatus.OutForDelivery -> R.string.feature_orders_status_out
        OrderStatus.Delivered -> R.string.feature_orders_status_delivered
        OrderStatus.Cancelled -> R.string.feature_orders_status_cancelled
    }

@Composable
internal fun returnLabel(status: ReturnStatus): String =
    stringResource(
        when (status) {
            ReturnStatus.Pending -> R.string.feature_orders_return_pending
            ReturnStatus.Approved -> R.string.feature_orders_return_approved
            ReturnStatus.Rejected -> R.string.feature_orders_return_rejected
        },
    )

/** An order's status as a coloured label, as on the website's order list. */
@Composable
internal fun StatusPill(
    status: OrderStatus,
    modifier: Modifier = Modifier,
) {
    val (container, content) =
        when (status) {
            OrderStatus.Delivered -> BookStoreTheme.colors.successContainer to BookStoreTheme.colors.success
            OrderStatus.Cancelled -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
            OrderStatus.Shipped, OrderStatus.OutForDelivery -> BookStoreTheme.colors.warningContainer to BookStoreTheme.colors.warning
            else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        }
    Pill(statusLabel(status), container, content, modifier)
}

@Composable
internal fun Pill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(container)
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
