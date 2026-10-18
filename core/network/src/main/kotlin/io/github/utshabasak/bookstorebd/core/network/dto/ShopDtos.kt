package io.github.utshabasak.bookstorebd.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class QuantityRequest(
    val quantity: Int,
)

/**
 * POST /cart/add/:id. With a quantity, that many copies, replacing what the
 * cart held; without one (sent as `{}`), the copies already there stay, or one.
 */
@Serializable
data class AddToCartRequest(
    val quantity: Int? = null,
)

@Serializable
data class OrderItemDto(
    val bookId: String,
    val quantity: Int,
)

@Serializable
data class CreateOrderRequest(
    val items: List<OrderItemDto>,
    /** Made once per checkout and sent with every try of it: a retry gets the first order back. */
    val checkoutId: String? = null,
    val promo: String? = null,
    val paymentMethod: String = "Cash on Delivery",
    val contactName: String,
    /** Where the e-mails about this order go; the account's own address when left out. */
    val contactEmail: String? = null,
    val contactPhone: String,
    val deliveryDivision: String,
    val deliveryDistrict: String,
    val deliveryAddress: String,
    val buyerNote: String? = null,
)

@Serializable
data class CreateOrderResponse(
    val message: String = "",
    val orderNumber: String,
    val shippingCharge: Double = 0.0,
    val discount: Double = 0.0,
    val promoMessage: String? = null,
    val unavailable: List<UnavailableItemDto> = emptyList(),
    /** True when this answers a checkout id already placed: no new order was made. */
    val replayed: Boolean = false,
)

/** A book left out of an order, or ordered in fewer copies than asked. */
@Serializable
data class UnavailableItemDto(
    val bookId: String,
    val title: String? = null,
    val available: Int = 0,
    val requested: Int = 0,
    val reserved: Int = 0,
    /** sold-out, short, removed, own-listing or seller-unavailable. */
    val reason: String = "",
)

@Serializable
data class CheckPromoRequest(
    val code: String,
    val booksTotal: Double,
)

@Serializable
data class CheckPromoResponse(
    val code: String,
    val description: String = "",
    val discount: Double = 0.0,
    val freeDelivery: Boolean = false,
)

@Serializable
data class AppConfigDto(
    val android: AndroidConfigDto,
    val auth: AuthConfigDto,
    val shop: ShopConfigDto,
    val links: LinksDto,
    val contact: ContactDto = ContactDto(),
)

@Serializable
data class ContactDto(
    val email: String = "",
    val location: String = "",
)

@Serializable
data class AndroidConfigDto(
    val minVersionCode: Int = 0,
    val latest: AppReleaseDto? = null,
)

@Serializable
data class AppReleaseDto(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val apkSize: Long = 0,
    val sha256: String,
    val notes: String = "",
    val pageUrl: String = "",
)

@Serializable
data class AuthConfigDto(
    val captchaSiteKey: String? = null,
    val challengeUrl: String? = null,
    val google: Boolean = false,
    val googleClientId: String? = null,
)

@Serializable
data class ShopConfigDto(
    val delivery: DeliveryDto,
    val returnWindowDays: Int = 7,
    val categories: List<CategoryGroupDto> = emptyList(),
    val divisions: List<DivisionDto> = emptyList(),
    val promotions: PromotionsDto = PromotionsDto(),
)

/** The codes checkout tells shoppers about, while they run. */
@Serializable
data class PromotionsDto(
    val firstOrder: PromotionDto? = null,
    val freeDelivery: PromotionDto? = null,
)

@Serializable
data class PromotionDto(
    val code: String,
    val description: String = "",
    val minBooksTotal: Int = 0,
)

@Serializable
data class DeliveryDto(
    val insideDhaka: Int = 70,
    val outsideDhaka: Int = 120,
    val daysInsideDhaka: Int = 3,
    val daysOutsideDhaka: Int = 5,
)

@Serializable
data class CategoryGroupDto(
    val name: String,
    val emoji: String = "",
    val items: List<String> = emptyList(),
)

@Serializable
data class DivisionDto(
    val name: String,
    val districts: List<String> = emptyList(),
)

@Serializable
data class LinksDto(
    val site: String = "",
    val privacy: String = "",
    val terms: String = "",
    val returns: String = "",
    val howItWorks: String = "",
    val about: String = "",
    val contact: String = "",
)

/** Every non-2xx answer carries at least a message (ApiError in api.d.ts). */
@Serializable
data class ApiErrorDto(
    val message: String? = null,
    val code: String? = null,
    val errors: List<ValidationIssueDto> = emptyList(),
    /** account-has-open-business: one sentence for each thing in the way. */
    val blockers: List<String> = emptyList(),
    /** A checkout where nothing could be ordered: the books, and why. */
    val unavailable: List<UnavailableItemDto> = emptyList(),
)

@Serializable
data class ValidationIssueDto(
    val path: String = "",
    val message: String = "",
)
