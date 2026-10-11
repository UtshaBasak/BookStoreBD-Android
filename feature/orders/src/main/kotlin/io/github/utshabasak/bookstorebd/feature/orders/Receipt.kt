package io.github.utshabasak.bookstorebd.feature.orders

import android.content.res.Resources
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withTranslation
import io.github.utshabasak.bookstorebd.core.model.Order
import io.github.utshabasak.bookstorebd.core.model.OrderStatus
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatDate
import io.github.utshabasak.bookstorebd.core.ui.formatTaka
import java.io.OutputStream
import java.util.Locale

/** One book on the receipt; a cancelled one is struck through and left out of the totals. */
internal data class ReceiptLine(
    val title: String,
    val detail: String,
    val quantity: String,
    val price: String,
    val total: String,
    val cancelled: Boolean,
)

/**
 * An order as the buyer's receipt, as the website's "Download PDF" lays it
 * out: the order, where it goes, every book, and what is paid.
 */
internal data class Receipt(
    val title: String,
    val orderNumber: String,
    val orderHeading: String,
    val orderFacts: List<Pair<String, String>>,
    val deliveryHeading: String,
    val deliveryFacts: List<Pair<String, String>>,
    val columns: List<String>,
    val lines: List<ReceiptLine>,
    val totals: List<Pair<String, String>>,
    val note: String?,
    val footer: List<String>,
)

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

/**
 * Draws [receipt] as an A4 PDF. Android's own text layout shapes Bangla
 * correctly, so titles in either language print as they read on screen.
 */
internal fun writeReceiptPdf(
    receipt: Receipt,
    out: OutputStream,
) {
    val document = PdfDocument()
    try {
        ReceiptPainter(document).draw(receipt)
        document.writeTo(out)
    } finally {
        document.close()
    }
}

private class ReceiptPainter(
    private val document: PdfDocument,
) {
    private var pageNumber = 0
    private lateinit var page: PdfDocument.Page
    private var y = 0f

    private val canvas get() = page.canvas

    fun draw(receipt: Receipt) {
        newPage()
        header(receipt)
        factBoxes(receipt)
        table(receipt)
        totals(receipt.totals)
        receipt.note?.let { note(it) }
        footer(receipt.footer)
        document.finishPage(page)
    }

    private fun newPage() {
        if (pageNumber > 0) document.finishPage(page)
        pageNumber += 1
        page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        y = MARGIN
    }

    /** Starts a new page when [height] more would run off this one. */
    private fun room(height: Float) {
        if (y + height > PAGE_HEIGHT - MARGIN) newPage()
    }

    private fun header(receipt: Receipt) {
        val brand = text(SIZE_BRAND, INK, bold = true)
        canvas.drawText(BRAND_START, MARGIN, y + SIZE_BRAND, brand)
        val bd = text(SIZE_BRAND, SUNSET, bold = true)
        canvas.drawText(BRAND_END, MARGIN + brand.measureText(BRAND_START), y + SIZE_BRAND, bd)

        val title = text(SIZE_TITLE, VIOLET, bold = true).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(receipt.title, PAGE_WIDTH - MARGIN, y + SIZE_TITLE, title)
        val number = text(SIZE_BODY, INK, bold = true, mono = true).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(receipt.orderNumber, PAGE_WIDTH - MARGIN, y + SIZE_TITLE + GAP + SIZE_BODY, number)

        y += SIZE_TITLE + GAP + SIZE_BODY + GAP * 2
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + RULE_THICK, fill(VIOLET))
        y += RULE_THICK + SECTION_GAP
    }

    private fun factBoxes(receipt: Receipt) {
        val half = (CONTENT_WIDTH - GAP * 2) / 2
        val left = facts(receipt.orderFacts, half)
        val right = facts(receipt.deliveryFacts, half)
        val height = maxOf(left.sumOf { it.height }, right.sumOf { it.height }) + SIZE_SMALL + PADDING * 2 + PADDING / 2
        room(height)
        box(MARGIN, half, height, receipt.orderHeading, left)
        box(MARGIN + half + GAP * 2, half, height, receipt.deliveryHeading, right)
        y += height + SECTION_GAP
    }

    private fun facts(
        pairs: List<Pair<String, String>>,
        width: Float,
    ): List<StaticLayout> =
        pairs.map { (label, value) ->
            layout("$label: $value", text(SIZE_BODY, INK), (width - PADDING * 2).toInt())
        }

    private fun box(
        x: Float,
        width: Float,
        height: Float,
        heading: String,
        lines: List<StaticLayout>,
    ) {
        canvas.drawRoundRect(RectF(x, y, x + width, y + height), RADIUS, RADIUS, stroke(LINE))
        var top = y + PADDING
        canvas.drawText(heading.uppercase(), x + PADDING, top + SIZE_SMALL, text(SIZE_SMALL, VIOLET, bold = true))
        top += SIZE_SMALL + PADDING / 2
        for (line in lines) {
            canvas.withTranslation(x + PADDING, top) { line.draw(this) }
            top += line.height
        }
    }

    private fun table(receipt: Receipt) {
        val numbers = CONTENT_WIDTH * NUMBER_SHARE
        val titleWidth = CONTENT_WIDTH - numbers * NUMBER_COLUMNS
        room(SIZE_SMALL + PADDING * 2)
        val head = text(SIZE_SMALL, MUTED, bold = true)
        cells(receipt.columns.map { it.uppercase() }, head, titleWidth, numbers, y + SIZE_SMALL)
        y += SIZE_SMALL + PADDING
        rule()
        for (line in receipt.lines) {
            val color = if (line.cancelled) MUTED else INK
            val title = layout(line.title, text(SIZE_BODY, color, bold = true, struck = line.cancelled), titleWidth.toInt())
            val detail = layout(line.detail, text(SIZE_SMALL, MUTED), titleWidth.toInt())
            val height = title.height + (if (line.detail.isBlank()) 0 else detail.height) + PADDING * 2
            room(height)
            canvas.withTranslation(MARGIN, y + PADDING) {
                title.draw(this)
                if (line.detail.isNotBlank()) {
                    translate(0f, title.height.toFloat())
                    detail.draw(this)
                }
            }
            cells(
                listOf("", line.quantity, line.price, line.total),
                text(SIZE_BODY, color, struck = line.cancelled),
                titleWidth,
                numbers,
                y + PADDING + SIZE_BODY,
            )
            y += height
            rule()
        }
        y += SECTION_GAP
    }

    /** A row: the first cell to the left, the numbers to the right of their columns. */
    private fun cells(
        values: List<String>,
        paint: TextPaint,
        titleWidth: Float,
        numbers: Float,
        baseline: Float,
    ) {
        if (values[0].isNotEmpty()) canvas.drawText(values[0], MARGIN, baseline, paint)
        val right = TextPaint(paint).apply { textAlign = Paint.Align.RIGHT }
        values.drop(1).forEachIndexed { index, value ->
            canvas.drawText(value, MARGIN + titleWidth + numbers * (index + 1), baseline, right)
        }
    }

    private fun totals(pairs: List<Pair<String, String>>) {
        val width = CONTENT_WIDTH * TOTALS_SHARE
        val x = PAGE_WIDTH - MARGIN - width
        val rowHeight = SIZE_BODY + GAP * 2
        val height = rowHeight * pairs.size + PADDING * 2 + GAP
        room(height)
        canvas.drawRoundRect(RectF(x, y, x + width, y + height), RADIUS, RADIUS, fill(TINT))
        var baseline = y + PADDING + SIZE_BODY
        pairs.forEachIndexed { index, (label, value) ->
            val last = index == pairs.lastIndex
            if (last) {
                canvas.drawRect(
                    x + PADDING,
                    baseline - SIZE_BODY - GAP,
                    x + width - PADDING,
                    baseline - SIZE_BODY - GAP + 1,
                    fill(VIOLET_SOFT),
                )
                baseline += GAP
            }
            val size = if (last) SIZE_TOTAL else SIZE_BODY
            canvas.drawText(label, x + PADDING, baseline, text(size, if (last) INK else MUTED, bold = last))
            val amount = text(size, INK, bold = true).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText(value, x + width - PADDING, baseline, amount)
            baseline += rowHeight
        }
        y += height + SECTION_GAP
    }

    private fun note(note: String) {
        val layout = layout(note, text(SIZE_BODY, NOTE_INK), (CONTENT_WIDTH - PADDING * 2).toInt())
        val height = layout.height + PADDING * 2
        room(height)
        canvas.drawRoundRect(RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + height), RADIUS, RADIUS, fill(NOTE_TINT))
        canvas.withTranslation(MARGIN + PADDING, y + PADDING) { layout.draw(this) }
        y += height + SECTION_GAP
    }

    private fun footer(lines: List<String>) {
        val layouts = lines.map { layout(it, text(SIZE_SMALL, MUTED), CONTENT_WIDTH.toInt()) }
        room(layouts.sumOf { it.height } + PADDING)
        rule()
        y += PADDING
        for (layout in layouts) {
            canvas.withTranslation(MARGIN, y) { layout.draw(this) }
            y += layout.height
        }
    }

    private fun rule() {
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 1, fill(LINE))
    }

    private fun layout(
        value: String,
        paint: TextPaint,
        width: Int,
    ): StaticLayout =
        StaticLayout.Builder
            .obtain(value, 0, value.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, LINE_SPACING)
            .build()

    private fun text(
        size: Float,
        color: Int,
        bold: Boolean = false,
        mono: Boolean = false,
        struck: Boolean = false,
    ) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(if (mono) Typeface.MONOSPACE else Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        isStrikeThruText = struck
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun stroke(color: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
}

// A4 in points, with the website's receipt colours.
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 40f
private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2
private const val PADDING = 10f
private const val GAP = 4f
private const val SECTION_GAP = 12f
private const val NUMBER_COLUMNS = 3
private const val RADIUS = 8f
private const val RULE_THICK = 2f
private const val LINE_SPACING = 1.15f
private const val NUMBER_SHARE = 0.13f
private const val TOTALS_SHARE = 0.55f
private const val SIZE_BRAND = 20f
private const val SIZE_TITLE = 14f
private const val SIZE_TOTAL = 12f
private const val SIZE_BODY = 10f
private const val SIZE_SMALL = 8f
private const val BRAND_START = "BookStore"
private const val BRAND_END = "BD"
private val INK = Color.rgb(17, 24, 39)
private val MUTED = Color.rgb(107, 114, 128)
private val VIOLET = Color.rgb(109, 40, 217)
private val VIOLET_SOFT = Color.rgb(221, 214, 254)
private val SUNSET = Color.rgb(255, 92, 53)
private val LINE = Color.rgb(229, 231, 235)
private val TINT = Color.rgb(243, 239, 255)
private val NOTE_TINT = Color.rgb(255, 247, 237)
private val NOTE_INK = Color.rgb(154, 52, 18)
