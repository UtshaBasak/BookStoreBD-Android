package io.github.utshabasak.bookstorebd.core.model

/** Everything the app learns from the server before its first screen. */
data class AppConfig(
    val minVersionCode: Int,
    val latestRelease: AppRelease?,
    val captchaSiteKey: String?,
    val challengeUrl: String?,
    val googleClientId: String?,
    val delivery: DeliveryRules,
    val returnWindowDays: Int,
    val categoryGroups: List<CategoryGroup>,
    val divisions: List<Division>,
    val links: SiteLinks,
    val promotions: Promotions = Promotions(),
) {
    val captchaRequired: Boolean get() = captchaSiteKey != null && challengeUrl != null
}

data class AppRelease(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String,
    val notes: String,
    val pageUrl: String,
)

/** The codes checkout tells shoppers about, while they run; the server prices every code. */
data class Promotions(
    val firstOrder: AdvertisedPromo? = null,
    val freeDelivery: AdvertisedPromo? = null,
)

data class AdvertisedPromo(
    val code: String,
    val description: String,
    /** Taka of books the code needs; 0 when it needs none. */
    val minBooksTotal: Int = 0,
)

data class DeliveryRules(
    val insideDhaka: Int,
    val outsideDhaka: Int,
    val daysInsideDhaka: Int,
    val daysOutsideDhaka: Int,
) {
    /** "Inside Dhaka" is Dhaka district, not the whole division, as on the website. */
    fun chargeFor(district: String?): Int = if (district.equals("Dhaka", ignoreCase = true)) insideDhaka else outsideDhaka
}

data class CategoryGroup(
    val name: String,
    val emoji: String,
    val items: List<String>,
)

data class Division(
    val name: String,
    val districts: List<String>,
)

data class SiteLinks(
    val site: String,
    val privacy: String,
    val terms: String,
    val returns: String,
    val howItWorks: String,
    val about: String = "",
    val contact: String = "",
    /** The shop's e-mail address and where it is, for "Get in touch"; blank when not known. */
    val email: String = "",
    val location: String = "",
)

/** A promo code the server accepted, and what it does. */
data class Promo(
    val code: String,
    val description: String,
    val discount: Double,
    val freeDelivery: Boolean,
)

/** What checkout sends. Delivery and discount are priced by the server. */
data class OrderRequest(
    val items: List<Pair<String, Int>>,
    val contactName: String,
    val contactPhone: String,
    val division: String,
    val district: String,
    val address: String,
    val note: String,
    val promo: String?,
)

/** A placed order, as the confirmation screen shows it. */
data class PlacedOrder(
    val orderNumber: String,
    val shippingCharge: Double,
    val discount: Double,
    val promoMessage: String?,
    /** Books that sold out before the order reached them, and how many were left. */
    val unavailable: List<UnavailableItem>,
)

data class UnavailableItem(
    val bookId: String,
    val title: String,
    val available: Int,
)
