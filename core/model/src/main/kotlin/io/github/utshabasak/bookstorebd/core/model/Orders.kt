package io.github.utshabasak.bookstorebd.core.model

/** An order's progress, in the order it happens. */
enum class OrderStatus(
    val apiValue: String,
) {
    Confirmed("Order Confirmed"),
    Processing("Processing"),
    Shipped("Shipped"),
    OutForDelivery("Out for Delivery"),
    Delivered("Delivered"),
    Cancelled("Cancelled"),
    ;

    companion object {
        /** The steps a delivered order went through, for the tracking stepper. */
        val steps = listOf(Confirmed, Processing, Shipped, OutForDelivery, Delivered)

        fun from(value: String?): OrderStatus = entries.firstOrNull { it.apiValue.equals(value, ignoreCase = true) } ?: Confirmed
    }
}

enum class ReturnStatus { Pending, Approved, Rejected }

enum class CancelledBy { Buyer, Seller, Admin, Unknown }

/** One book in an order, as the order lists and tracking pages show it. */
data class OrderLine(
    val id: String,
    val orderNumber: String,
    val status: OrderStatus,
    val bookId: String,
    val title: String,
    val author: String,
    val condition: String?,
    val type: BookType,
    /** The price paid per copy, after any discount. */
    val price: Double,
    val listPrice: Double,
    val quantity: Int,
    val sellerEmail: String,
    val createdAt: String?,
    val deliveredAt: String?,
    val cancelledAt: String?,
    val cancelledBy: CancelledBy?,
    val cancelReason: String,
    val returnStatus: ReturnStatus?,
    /** Until when a return can be asked for, once delivered; null when it cannot. */
    val returnableUntil: String?,
    /** The book's cover, served by the shop for every listing. */
    val coverUrl: String? = null,
) {
    val lineTotal: Double get() = price * quantity
    val isCancelled: Boolean get() = status == OrderStatus.Cancelled
}

/** The contact and delivery details an order was placed with. */
data class OrderDelivery(
    val name: String,
    val phone: String,
    val division: String,
    val district: String,
    val address: String,
    val note: String,
    val paymentMethod: String,
)

/**
 * One order, which may hold books from several sellers. Cancelled books are
 * left out of the totals, as on the website.
 */
data class Order(
    val orderNumber: String,
    val status: OrderStatus,
    val placedAt: String?,
    val lines: List<OrderLine>,
    val booksTotal: Double,
    val shipping: Double,
    val discount: Double,
    val total: Double,
    val promo: String?,
    val delivery: OrderDelivery,
    /** True when the buyer may still cancel; only on the tracking page's answer. */
    val canCancel: Boolean = false,
) {
    /** Lines that can still be returned, with the return window open. */
    val returnable: List<OrderLine> get() = lines.filter { it.returnableUntil != null && it.returnStatus == null && !it.isCancelled }
    val cancelledCount: Int get() = lines.count { it.isCancelled }
}

enum class OrderSort(
    val apiValue: String,
) {
    Newest("newest"),
    Oldest("oldest"),
    TotalHigh("totalHigh"),
    TotalLow("totalLow"),
}

data class OrderQuery(
    val search: String = "",
    val status: OrderStatus? = null,
    val sort: OrderSort = OrderSort.Newest,
)

/** A return request as the buyer sends it. */
data class ReturnDraft(
    val lineIds: List<String>,
    val description: String,
    val refundBkash: String,
    val photos: List<ImageUpload>,
)
