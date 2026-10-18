package io.github.utshabasak.bookstorebd.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Page<T> in api.d.ts. */
@Serializable
data class PageDto<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 0,
    val pageCount: Int = 0,
)

/**
 * OrderLine, BuyerOrderLine and the line fields of OrderDetail in one type:
 * the order-level fields repeat on every line.
 */
@Serializable
data class OrderLineDto(
    @SerialName("_id") val id: String,
    val orderNumber: String,
    val status: String = "",
    val deliveredAt: String? = null,
    val buyerEmail: String = "",
    val sellerEmail: String = "",
    val bookId: String = "",
    val title: String = "",
    val author: String = "",
    val bookType: String = "",
    val condition: String? = null,
    val price: Double = 0.0,
    val listPrice: Double? = null,
    val quantity: Int = 1,
    val paymentMethod: String = "",
    val contactName: String = "",
    val contactPhone: String = "",
    /** Where the buyer's e-mails about the order go; blank on older orders. */
    val contactEmail: String = "",
    val deliveryDivision: String = "",
    val deliveryDistrict: String = "",
    val deliveryAddress: String = "",
    val shippingCharge: Double = 0.0,
    val discount: Double = 0.0,
    val promo: String? = null,
    val buyerNote: String = "",
    val cancelledAt: String? = null,
    val cancelledBy: String = "",
    val cancelReason: String = "",
    val createdAt: String? = null,
    // BuyerOrderLine
    val booksTotal: Double? = null,
    val shippingCost: Double? = null,
    val totalCost: Double? = null,
    /** Where the whole order stands: the least advanced of its live books, or Cancelled. */
    val orderStatus: String? = null,
    /** Whether the buyer may cancel the whole order now. */
    val canCancel: Boolean? = null,
    // BuyerOrderLine, and each book of OrderDetail
    val returnStatus: String? = null,
    val returnableUntil: String? = null,
    // SellerOrderLine
    val payoutState: String? = null,
    val payableFrom: String? = null,
    val sellerPaidAt: String? = null,
    val sellerPayoutRef: String = "",
)

/** GET /order/:orderNumber: the first line's fields, the order's lines and its totals. */
@Serializable
data class OrderDetailDto(
    @SerialName("_id") val id: String = "",
    val orderNumber: String,
    val status: String = "",
    val createdAt: String? = null,
    val paymentMethod: String = "",
    val contactName: String = "",
    val contactPhone: String = "",
    val deliveryDivision: String = "",
    val deliveryDistrict: String = "",
    val deliveryAddress: String = "",
    val buyerNote: String = "",
    val promo: String? = null,
    val books: List<OrderLineDto> = emptyList(),
    val booksTotal: Double = 0.0,
    val shippingCost: Double = 0.0,
    val discount: Double = 0.0,
    val totalCost: Double = 0.0,
    val canCancel: Boolean = false,
    /** The statuses the viewer may move the order to; empty for a buyer. */
    val statusOptions: List<String> = emptyList(),
    val buyerEmail: String = "",
)

@Serializable
data class CancelOrderRequest(
    val reason: String? = null,
)

@Serializable
data class CancelOrderDto(
    val message: String = "",
    val cancelled: Int = 0,
)

/** GET /upload/signature: lets the app upload straight to the image host. */
@Serializable
data class UploadSignatureDto(
    val signature: String,
    val timestamp: Long,
    val folder: String,
    val apiKey: String,
    val cloudName: String = "",
    val uploadUrl: String,
)

/** The image host's answer to an upload. */
@Serializable
data class HostedImageDto(
    @SerialName("secure_url") val secureUrl: String,
    @SerialName("public_id") val publicId: String,
)

@Serializable
data class CreateReturnDto(
    val message: String = "",
    val returnId: String? = null,
    val returnIds: List<String> = emptyList(),
)
