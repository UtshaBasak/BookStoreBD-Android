package io.github.utshabasak.bookstorebd.core.ui.navigation

import androidx.navigation3.runtime.NavKey
import io.github.utshabasak.bookstorebd.core.model.CatalogueQuery
import io.github.utshabasak.bookstorebd.core.model.CatalogueSort
import kotlinx.serialization.Serializable

/*
 * Every screen, as a key on the back stack. Serializable, so the stacks
 * survive the app being stopped in the background.
 */

@Serializable data object HomeRoute : NavKey

/** The catalogue, opened with a search or filter already applied. */
@Serializable
data class CatalogueRoute(
    val search: String = "",
    val category: String? = null,
    val dealsOnly: Boolean = false,
    val sort: CatalogueSort = CatalogueSort.Relevant,
    /** Open with the search box focused, ready to type. */
    val focusSearch: Boolean = false,
) : NavKey {
    fun toQuery(): CatalogueQuery =
        CatalogueQuery(search = search, categories = setOfNotNull(category), dealsOnly = dealsOnly, sort = sort)
}

@Serializable data class BookRoute(
    val id: String,
) : NavKey

@Serializable data class ShopRoute(
    val username: String,
) : NavKey

@Serializable data object WishlistRoute : NavKey

@Serializable data object CartRoute : NavKey

@Serializable data object CheckoutRoute : NavKey

/** The confirmation after checkout: what was ordered, what it costs on delivery, and anything that sold out first. */
@Serializable data class OrderPlacedRoute(
    val orderNumber: String,
    val total: Double,
    val unavailable: List<String> = emptyList(),
    val promoMessage: String? = null,
) : NavKey

@Serializable data object AccountRoute : NavKey

@Serializable data object SignInRoute : NavKey

@Serializable data object SignUpRoute : NavKey

@Serializable data object ResetPasswordRoute : NavKey

/** The profile form, scrolled to one part of it when [focus] names a setup step (SetupItem's name). */
@Serializable data class EditProfileRoute(
    val focus: String? = null,
) : NavKey

/** Privacy and data: what the app remembers on this phone, downloading your data, deleting the account. */
@Serializable data object PrivacyRoute : NavKey

@Serializable data object InviteRoute : NavKey

/** The person's orders; [booksTab] opens the list of books bought instead. */
@Serializable data class OrdersRoute(
    val booksTab: Boolean = false,
) : NavKey

/** One order's tracking page. */
@Serializable data class OrderRoute(
    val orderNumber: String,
) : NavKey

/** Asking to return books from one delivered order. */
@Serializable data class ReturnRoute(
    val orderNumber: String,
    val lineIds: List<String>,
    val titles: List<String>,
) : NavKey

/** Writing or editing a review of a book (`book`) or a rating of a seller (`seller`). */
@Serializable data class WriteReviewRoute(
    val kind: String,
    val targetId: String,
    val name: String,
) : NavKey

/** The Wanted board, with the request form open and filled in when [ask] is given. */
@Serializable data class WantedRoute(
    val ask: String? = null,
) : NavKey

/** The notification bell's list. */
@Serializable data object NotificationsRoute : NavKey

/** Which kinds of notification arrive in the app and by e-mail. */
@Serializable data object NotificationSettingsRoute : NavKey

/** The person's conversations with buyers and sellers. */
@Serializable data object MessagesRoute : NavKey

/** One conversation, with the person at [email]; [name] is shown until their details load. */
@Serializable data class ChatRoute(
    val email: String,
    val name: String = "",
) : NavKey

/** Listing a book for sale, filled in from a Wanted-board request when one is given. */
@Serializable data class ListBookRoute(
    val title: String = "",
    val author: String = "",
    val isbn: String = "",
) : NavKey

/** The seller's own listings: price, stock, discount, and deleting. */
@Serializable data object MyBooksRoute : NavKey

/** Orders for the seller's books. */
@Serializable data object SalesRoute : NavKey

/** One order, as its seller sees it. */
@Serializable data class SaleRoute(
    val orderNumber: String,
) : NavKey

/** Moves between screens. The app owns the back stacks; screens only ask. */
interface Navigator {
    fun navigate(route: NavKey)

    fun back()

    /** Replaces the current screen, so back skips it: after signing in, after an order. */
    fun replace(route: NavKey)
}
