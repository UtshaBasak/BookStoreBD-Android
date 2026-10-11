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

/** Moves between screens. The app owns the back stacks; screens only ask. */
interface Navigator {
    fun navigate(route: NavKey)

    fun back()

    /** Replaces the current screen, so back skips it: after signing in, after an order. */
    fun replace(route: NavKey)
}
