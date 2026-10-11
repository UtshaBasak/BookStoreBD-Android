package io.github.utshabasak.bookstorebd.core.model

import kotlin.math.max
import kotlin.math.roundToInt

/** A seller's discount: none, a percentage off, or an amount of taka off. */
enum class DiscountKind(
    val apiValue: String,
) {
    None("none"),
    Percent("percent"),
    Amount("amount"),
    ;

    companion object {
        fun from(value: String?): DiscountKind = entries.firstOrNull { it.apiValue == value } ?: None
    }
}

data class Discount(
    val kind: DiscountKind = DiscountKind.None,
    val value: Int = 0,
)

/** Why a discount does not fit a price, as the website words the rule. */
enum class DiscountProblem { NotWhole, FreeBook, PercentTooLarge, NotBelowPrice, AmountTooLarge }

/**
 * The website's pricing rules (server/config/pricing.ts), so the app shows the
 * same "sells for" figure and refuses the same discounts before sending them.
 */
object Pricing {
    const val MAX_DISCOUNT_PERCENT = 90

    /** The share of each sale the shop keeps, on the books only. */
    const val SELLER_FEE_PERCENT = 5

    fun problem(
        price: Int,
        discount: Discount,
    ): DiscountProblem? {
        val value = discount.value
        return when {
            discount.kind == DiscountKind.None -> null
            value < 1 -> DiscountProblem.NotWhole
            price <= 0 -> DiscountProblem.FreeBook
            discount.kind == DiscountKind.Percent && value > MAX_DISCOUNT_PERCENT -> DiscountProblem.PercentTooLarge
            discount.kind == DiscountKind.Amount && value >= price -> DiscountProblem.NotBelowPrice
            discount.kind == DiscountKind.Amount && value * PERCENT > price * MAX_DISCOUNT_PERCENT -> DiscountProblem.AmountTooLarge
            else -> null
        }
    }

    /** What a copy sells for; the price itself when the discount does not fit. */
    fun salePrice(
        price: Int,
        discount: Discount,
    ): Int =
        when {
            discount.kind == DiscountKind.None || problem(price, discount) != null -> price
            discount.kind == DiscountKind.Percent -> max(1, (price * (PERCENT - discount.value) / PERCENT.toDouble()).roundToInt())
            else -> price - discount.value
        }

    /** What the seller receives for [amount] of books, after the shop's fee. */
    fun sellerReceives(amount: Double): Double = amount - (amount * SELLER_FEE_PERCENT).roundToInt() / PERCENT.toDouble()

    private const val PERCENT = 100
}

/** One of the seller's own listings, with its discount and how many buyers wait for it while sold out. */
data class Listing(
    val book: Book,
    val discount: Discount,
    val createdAt: String?,
    val waiting: Int,
)

/** A new listing as the form holds it; numbers stay text until sent, as typed. */
data class ListingDraft(
    val title: String = "",
    val author: String = "",
    val publisher: String = "",
    val country: String = "",
    val language: String = "",
    val isbn: String = "",
    val pages: String = "",
    val description: String = "",
    val categories: Set<String> = emptySet(),
    val type: BookType = BookType.New,
    val condition: String = "mint",
    val conditionDetails: String = "",
    val price: String = "",
    val discount: Discount = Discount(),
)

/** What the server said to a new listing: its id, and how many Wanted-board readers were told. */
data class Published(
    val message: String,
    val bookId: String?,
    val waiting: Int,
)

/** Where a seller's money for one order line has got to. */
enum class PayoutState(
    val apiValue: String,
) {
    AwaitingDelivery("awaiting-delivery"),
    InReturnWindow("in-return-window"),
    ReturnInProgress("return-in-progress"),
    Returned("returned"),
    Due("due"),
    Paid("paid"),
    ;

    companion object {
        fun from(value: String?): PayoutState = entries.firstOrNull { it.apiValue == value } ?: AwaitingDelivery
    }
}

/** One of the seller's books in an order, with the buyer and where the payout stands. */
data class Sale(
    val line: OrderLine,
    val buyerEmail: String,
    val buyerNote: String,
    val payout: PayoutState,
    val payableFrom: String?,
    val paidAt: String?,
    val payoutRef: String,
)

data class SalesQuery(
    val search: String = "",
    val status: OrderStatus? = null,
    val sort: OrderSort = OrderSort.Newest,
)
