package io.github.utshabasak.bookstorebd.core.ui

import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.InviteRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrderRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrdersRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.ShopRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.WantedRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.routeForSiteLink
import org.junit.Test

class SiteLinksTest {
    @Test
    fun `notification links open the matching screen`() {
        assertThat(routeForSiteLink("/order-tracking/ABC123")).isEqualTo(OrderRoute("ABC123"))
        assertThat(routeForSiteLink("/book/6abd0acf#reviews")).isEqualTo(BookRoute("6abd0acf"))
        assertThat(routeForSiteLink("/shop/boipoka")).isEqualTo(ShopRoute("boipoka"))
        assertThat(routeForSiteLink("/buyer-books")).isEqualTo(OrdersRoute(booksTab = true))
        assertThat(routeForSiteLink("/buyer/orders")).isEqualTo(OrdersRoute())
        assertThat(routeForSiteLink("/profile#invite")).isEqualTo(InviteRoute)
        assertThat(routeForSiteLink("/wanted?mine=1")).isEqualTo(WantedRoute())
    }

    @Test
    fun `pages the app does not have yet are left to the website`() {
        assertThat(routeForSiteLink("/seller/order-tracking/ABC123")).isNull()
        assertThat(routeForSiteLink("/seller-books")).isNull()
        assertThat(routeForSiteLink("/admin/returns")).isNull()
    }
}
