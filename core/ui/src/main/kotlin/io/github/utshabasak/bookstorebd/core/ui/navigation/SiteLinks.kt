package io.github.utshabasak.bookstorebd.core.ui.navigation

import androidx.navigation3.runtime.NavKey
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.CatalogueSort
import java.net.URLDecoder

/**
 * The screen for a path on the website, as notifications and shared links name
 * them (`/order-tracking/ABC`, `/book/<id>#reviews`, `/filter?search=...`). Null
 * for pages the app does not have, such as the administrators' tools, which then
 * open on the website.
 */
fun routeForSiteLink(link: String): NavKey? {
    val path = link.substringBefore('#').substringBefore('?').trimEnd('/')
    val fragment = link.substringAfter('#', "")
    val query = queryOf(link)
    val segments = path.split('/').filter { it.isNotEmpty() }
    return when {
        segments.isEmpty() -> HomeRoute
        segments.size == 2 && segments[0] == "order-tracking" -> OrderRoute(segments[1])
        segments.size == 2 && segments[0] == "book" -> BookRoute(segments[1])
        segments.size == 2 && segments[0] == "shop" -> ShopRoute(segments[1])
        segments == listOf("buyer", "orders") -> OrdersRoute()
        segments.size == SELLER_ORDER_PARTS && segments[0] == "seller" && segments[1] == "order-tracking" -> SaleRoute(segments[2])
        segments.size > 1 -> null
        else -> singleSegment(segments[0], fragment, query)
    }
}

private fun singleSegment(
    segment: String,
    fragment: String,
    query: Map<String, String>,
): NavKey? =
    when (segment) {
        "profile" if fragment == "invite" -> InviteRoute
        "filter" -> catalogueFor(query)
        "wanted" -> WantedRoute(ask = query["ask"]?.takeIf { it.isNotBlank() })
        "add-book" -> ListBookRoute(query["title"].orEmpty(), query["author"].orEmpty(), query["isbn"].orEmpty())
        else -> PAGES[segment]
    }

/** The catalogue with the website's search and filters from `/filter?...`. */
private fun catalogueFor(query: Map<String, String>): CatalogueRoute =
    CatalogueRoute(
        search = query["search"].orEmpty(),
        category = query["category"]?.takeIf { it.isNotBlank() },
        dealsOnly = query["deals"] == "1",
        sort = CatalogueSort.entries.firstOrNull { it.apiValue == query["sort"] } ?: CatalogueSort.Relevant,
        type =
            when (query["bookType"]) {
                "new" -> BookType.New
                "old" -> BookType.Old
                else -> null
            },
        inStockOnly = query["inStock"] == "1",
        minPrice = query["minPrice"]?.toIntOrNull()?.takeIf { it >= 0 },
        maxPrice = query["maxPrice"]?.toIntOrNull()?.takeIf { it >= 0 },
    )

/** A link's query, decoded; the first value wins when a name repeats. */
private fun queryOf(link: String): Map<String, String> =
    link
        .substringBefore('#')
        .substringAfter('?', "")
        .split('&')
        .filter { it.isNotEmpty() }
        .mapNotNull { pair ->
            runCatching {
                URLDecoder.decode(pair.substringBefore('='), "UTF-8") to
                    URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
            }.getOrNull()
        }.reversed()
        .toMap()

/** Website pages with an address of one part, and the app's screen for each. */
private val PAGES: Map<String, NavKey> =
    mapOf(
        "buyer-books" to OrdersRoute(booksTab = true),
        "profile" to AccountRoute,
        "update-profile" to EditProfileRoute(),
        "wishlist" to WishlistRoute,
        "cart" to CartRoute,
        "notifications" to NotificationsRoute,
        "chat" to MessagesRoute,
        "seller-books" to MyBooksRoute,
        "seller-orders" to SalesRoute,
    )

/** `/seller/order-tracking/<number>` */
private const val SELLER_ORDER_PARTS = 3
