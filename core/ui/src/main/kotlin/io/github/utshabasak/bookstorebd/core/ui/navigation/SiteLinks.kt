package io.github.utshabasak.bookstorebd.core.ui.navigation

import androidx.navigation3.runtime.NavKey

/**
 * The screen for a path on the website, as notifications link to them
 * (`/order-tracking/ABC`, `/book/<id>#reviews`). Null for pages the app does
 * not have, such as a seller's tools, which then open on the website.
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
        segments.size > 1 -> null
        else -> singleSegment(segments[0], fragment)
    }
}

private fun singleSegment(
    segment: String,
    fragment: String,
): NavKey? =
    when (segment) {
        "buyer-books" -> OrdersRoute(booksTab = true)
        "profile" -> if (fragment == "invite") InviteRoute else AccountRoute
        "update-profile" -> EditProfileRoute()
        "wishlist" -> WishlistRoute
        "cart" -> CartRoute
        "wanted" -> WantedRoute()
        "notifications" -> NotificationsRoute
        "chat" -> MessagesRoute
        "filter" -> CatalogueRoute()
        else -> null
    }
