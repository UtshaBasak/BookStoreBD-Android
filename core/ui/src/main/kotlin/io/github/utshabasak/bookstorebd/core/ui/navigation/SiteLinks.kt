package io.github.utshabasak.bookstorebd.core.ui.navigation

import androidx.navigation3.runtime.NavKey

/**
 * The screen for a path on the website, as notifications link to them
 * (`/order-tracking/ABC`, `/book/<id>#reviews`). Null for pages the app does
 * not have, such as the administrators' tools, which then open on the website.
 */
fun routeForSiteLink(link: String): NavKey? {
    val path = link.substringBefore('#').substringBefore('?').trimEnd('/')
    val fragment = link.substringAfter('#', "")
    val segments = path.split('/').filter { it.isNotEmpty() }
    return when {
        segments.isEmpty() -> HomeRoute
        segments.size == 2 && segments[0] == "order-tracking" -> OrderRoute(segments[1])
        segments.size == 2 && segments[0] == "book" -> BookRoute(segments[1])
        segments.size == 2 && segments[0] == "shop" -> ShopRoute(segments[1])
        segments == listOf("buyer", "orders") -> OrdersRoute()
        segments.size == SELLER_ORDER_PARTS && segments[0] == "seller" && segments[1] == "order-tracking" -> SaleRoute(segments[2])
        segments.size > 1 -> null
        else -> singleSegment(segments[0], fragment)
    }
}

private fun singleSegment(
    segment: String,
    fragment: String,
): NavKey? = if (segment == "profile" && fragment == "invite") InviteRoute else PAGES[segment]

/** Website pages with an address of one part, and the app's screen for each. */
private val PAGES: Map<String, NavKey> =
    mapOf(
        "buyer-books" to OrdersRoute(booksTab = true),
        "profile" to AccountRoute,
        "update-profile" to EditProfileRoute(),
        "wishlist" to WishlistRoute,
        "cart" to CartRoute,
        "wanted" to WantedRoute(),
        "notifications" to NotificationsRoute,
        "chat" to MessagesRoute,
        "filter" to CatalogueRoute(),
        "seller-books" to MyBooksRoute,
        "seller-orders" to SalesRoute,
        "add-book" to ListBookRoute(),
    )

/** `/seller/order-tracking/<number>` */
private const val SELLER_ORDER_PARTS = 3
