package io.github.utshabasak.bookstorebd.feature.orders

import android.content.res.Resources
import io.github.utshabasak.bookstorebd.core.model.Order
import io.github.utshabasak.bookstorebd.core.model.OrderStatus
import io.github.utshabasak.bookstorebd.core.ui.Receipt
import io.github.utshabasak.bookstorebd.core.ui.ReceiptLine
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatDate
import io.github.utshabasak.bookstorebd.core.ui.formatTaka
import java.util.Locale

internal fun buildReceipt(
    order: Order,
    res: Resources,
    locale: Locale,
    downloadedAt: String,
): Receipt {
    fun taka(amount: Double) = formatTaka(amount, locale)
    val delivery = order.delivery
    val area = listOf(delivery.district, delivery.division).filter { it.isNotBlank() }.joinToString(", ")
    val cancelled = res.getString(R.string.feature_orders_receipt_cancelled)
    return Receipt(
        title = res.getString(R.string.feature_orders_receipt_title),
        orderNumber = "#" + order.orderNumber,
        orderHeading = res.getString(R.string.feature_orders_receipt_order),
        orderFacts =
            listOf(
                res.getString(R.string.feature_orders_receipt_placed) to formatDate(order.placedAt, locale, withTime = true),
                res.getString(R.string.feature_orders_receipt_status) to res.getString(statusRes(order.status)),
                res.getString(R.string.feature_orders_receipt_payment) to delivery.paymentMethod,
            ).filter { it.second.isNotBlank() },
        deliveryHeading = res.getString(R.string.feature_orders_receipt_deliver_to),
        deliveryFacts =
            listOf(
                res.getString(R.string.feature_orders_receipt_name) to delivery.name,
                res.getString(R.string.feature_orders_receipt_phone) to delivery.phone,
                res.getString(R.string.feature_orders_receipt_address) to
                    listOf(delivery.address, area).filter { it.isNotBlank() }.joinToString(", "),
            ).filter { it.second.isNotBlank() },
        columns =
            listOf(
                R.string.feature_orders_receipt_book,
                R.string.feature_orders_receipt_quantity,
                R.string.feature_orders_receipt_price,
                R.string.feature_orders_receipt_line_total,
            ).map(res::getString),
        lines =
            order.lines.map { line ->
                ReceiptLine(
                    title = line.title,
                    detail = listOfNotNull(line.author.ifBlank { null }, cancelled.takeIf { line.isCancelled }).joinToString(" · "),
                    quantity = formatCount(line.quantity, locale),
                    price = taka(line.price),
                    total = taka(line.lineTotal),
                    cancelled = line.isCancelled,
                )
            },
        totals = receiptTotals(order, res, ::taka),
        note = delivery.note.ifBlank { null }?.let { res.getString(R.string.feature_orders_receipt_note, it) },
        footer =
            listOf(
                res.getString(R.string.feature_orders_receipt_returns),
                res.getString(R.string.feature_orders_receipt_footer, downloadedAt),
            ),
    )
}

private fun receiptTotals(
    order: Order,
    res: Resources,
    taka: (Double) -> String,
): List<Pair<String, String>> =
    buildList {
        add(res.getString(R.string.feature_orders_receipt_books) to taka(order.booksTotal))
        add(
            res.getString(R.string.feature_orders_receipt_delivery) to
                if (order.shipping > 0) taka(order.shipping) else res.getString(R.string.feature_orders_receipt_free),
        )
        if (order.discount > 0) {
            val label =
                order.promo?.takeIf { it.isNotBlank() }?.let { res.getString(R.string.feature_orders_receipt_discount_code, it) }
                    ?: res.getString(R.string.feature_orders_receipt_discount)
            add(label to "- " + taka(order.discount))
        }
        add(res.getString(totalLabel(order.status)) to taka(order.total))
    }

private fun totalLabel(status: OrderStatus): Int =
    when (status) {
        OrderStatus.Delivered -> R.string.feature_orders_receipt_paid
        OrderStatus.Cancelled -> R.string.feature_orders_receipt_order_total
        else -> R.string.feature_orders_receipt_to_pay
    }
