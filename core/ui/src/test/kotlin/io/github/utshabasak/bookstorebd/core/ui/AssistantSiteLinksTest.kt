package io.github.utshabasak.bookstorebd.core.ui

import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.model.CatalogueSort
import io.github.utshabasak.bookstorebd.core.ui.navigation.AccountRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CartRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CatalogueRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.EditProfileRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.HomeRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.ListBookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.MessagesRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.MyBooksRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.NotificationsRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrderRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrdersRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SalesRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.ShopRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignInRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignUpRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.WantedRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.WishlistRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.routeForSiteLink
import org.junit.Test

/** The pages the shopping assistant links to, and the app's screen for each. */
class AssistantSiteLinksTest {
    @Test
    fun `what the assistant found opens in the app`() {
        assertThat(routeForSiteLink("/filter?search=Humayun%20Ahmed")).isEqualTo(CatalogueRoute(search = "Humayun Ahmed"))
        assertThat(routeForSiteLink("/filter?deals=1&sort=dealPercent"))
            .isEqualTo(CatalogueRoute(dealsOnly = true, sort = CatalogueSort.DealPercent))
        assertThat(routeForSiteLink("/book/665f0a")).isEqualTo(BookRoute("665f0a"))
        assertThat(routeForSiteLink("/shop/rahim_books")).isEqualTo(ShopRoute("rahim_books"))
        assertThat(routeForSiteLink("/order-tracking/BSB123")).isEqualTo(OrderRoute("BSB123"))
    }

    @Test
    fun `the shop's pages that the app has open as its own screens`() {
        val screens =
            mapOf(
                "/" to HomeRoute,
                "/filter" to CatalogueRoute(),
                "/wanted" to WantedRoute(),
                "/sign-in" to SignInRoute,
                "/sign-up" to SignUpRoute,
                "/cart" to CartRoute,
                "/wishlist" to WishlistRoute,
                "/chat" to MessagesRoute,
                "/notifications" to NotificationsRoute,
                "/profile" to AccountRoute,
                "/update-profile" to EditProfileRoute(),
                "/buyer/orders" to OrdersRoute(),
                "/add-book" to ListBookRoute(),
                "/seller-books" to MyBooksRoute,
                "/seller-orders" to SalesRoute,
            )

        screens.forEach { (path, screen) -> assertThat(routeForSiteLink(path)).isEqualTo(screen) }
    }

    @Test
    fun `information pages are left to the website, with their section`() {
        listOf("/how-it-works", "/about", "/contact", "/returns", "/terms", "/privacy", "/terms#cancelling", "/privacy#assistant")
            .forEach { path -> assertThat(routeForSiteLink(path)).isNull() }
    }
}
