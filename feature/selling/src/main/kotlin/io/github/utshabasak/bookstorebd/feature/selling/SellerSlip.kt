package io.github.utshabasak.bookstorebd.feature.selling

import android.content.res.Resources
import io.github.utshabasak.bookstorebd.core.model.Order
import io.github.utshabasak.bookstorebd.core.model.Pricing
import io.github.utshabasak.bookstorebd.core.ui.Receipt
import io.github.utshabasak.bookstorebd.core.ui.ReceiptLine
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatDate
import io.github.utshabasak.bookstorebd.core.ui.formatTaka
import java.util.Locale

/** The seller's books in an order. */
internal fun Order.booksTotal(): Double = lines.filterNot { it.isCancelled }.sumOf { it.lineTotal }

/**
 * The seller's order slip, as the website's: only their own books, the buyer's
 * name, e-mail and delivery area (not the phone or street address), and what
 * they will receive after the shop's fee.
 */
internal fun buildSellerSlip(
    order: Order,
    res: Resources,
    locale: Locale,
    returnWindowDays: Int,
    downloadedAt: String,
): Receipt {
    fun taka(amount: Double) = formatTaka(amount, locale)
    val delivery = order.delivery
    val area = listOf(delivery.district, delivery.division).filter { it.isNotBlank() }.joinToString(", ")
    val books = order.booksTotal()
    val cancelled = res.getString(R.string.feature_selling_slip_cancelled)
    return Receipt(
        title = res.getString(R.string.feature_selling_slip_title),
        orderNumber = "#" + order.orderNumber,
        orderHeading = res.getString(R.string.feature_selling_slip_order),
        orderFacts =
            listOf(
                res.getString(R.string.feature_selling_slip_placed) to formatDate(order.placedAt, locale, withTime = true),
                res.getString(R.string.feature_selling_slip_status) to res.getString(statusRes(order.status)),
                res.getString(R.string.feature_selling_slip_payment) to delivery.paymentMethod,
            ).filter { it.second.isNotBlank() },
        deliveryHeading = res.getString(R.string.feature_selling_slip_buyer),
        deliveryFacts =
            listOf(
                res.getString(R.string.feature_selling_slip_name) to delivery.name,
                res.getString(R.string.feature_selling_slip_email) to order.buyerEmail,
                res.getString(R.string.feature_selling_slip_area) to area,
            ).filter { it.second.isNotBlank() },
        columns =
            listOf(
                R.string.feature_selling_slip_book,
                R.string.feature_selling_slip_quantity,
                R.string.feature_selling_slip_price,
                R.string.feature_selling_slip_total,
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
        totals =
            listOf(
                res.getString(R.string.feature_selling_slip_your_books) to taka(books),
                res.getString(
                    R.string.feature_selling_slip_fee,
                    Pricing.SELLER_FEE_PERCENT,
                ) to "- " + taka(books - Pricing.sellerReceives(books)),
                res.getString(R.string.feature_selling_slip_receive) to taka(Pricing.sellerReceives(books)),
            ),
        note = delivery.note.ifBlank { null }?.let { res.getString(R.string.feature_selling_slip_note, it) },
        footer =
            listOf(
                res.getString(R.string.feature_selling_slip_paid_by, returnWindowDays),
                res.getString(R.string.feature_selling_slip_footer, downloadedAt),
            ),
    )
}
