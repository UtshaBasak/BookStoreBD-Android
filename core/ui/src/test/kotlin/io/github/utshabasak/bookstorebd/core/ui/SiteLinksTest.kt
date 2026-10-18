package io.github.utshabasak.bookstorebd.core.ui

import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.CatalogueSort
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CartRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CatalogueRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.HomeRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.InviteRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.ListBookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.MyBooksRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrderRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrdersRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SaleRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SalesRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.ShopRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.WantedRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.routeForSiteLink
import org.junit.Test

class SiteLinksTest {
    @Test
    fun `notification links open the matching screen`() {
        assertThat(routeForSiteLink("/order-tracking/ABC123")).isEqualTo(OrderRoute("ABC123"))
        assertThat(routeForSiteLink("/book/6abd0acf")).isEqualTo(BookRoute("6abd0acf"))
        // A "delivered" notice opens the book at its reviews.
        assertThat(routeForSiteLink("/book/6abd0acf#reviews")).isEqualTo(BookRoute("6abd0acf", reviews = true))
        assertThat(routeForSiteLink("/shop/boipoka")).isEqualTo(ShopRoute("boipoka"))
        // A shop's name is written into the address encoded.
        assertThat(routeForSiteLink("/shop/Boi%20Ghor")).isEqualTo(ShopRoute("Boi Ghor"))
        assertThat(routeForSiteLink("/shop/C%2B%2B%20Books")).isEqualTo(ShopRoute("C++ Books"))
        assertThat(routeForSiteLink("/shop/%E0%A6%AC%E0%A6%87")).isEqualTo(ShopRoute("বই"))
        assertThat(routeForSiteLink("/buyer-books")).isEqualTo(OrdersRoute(booksTab = true))
        assertThat(routeForSiteLink("/buyer/orders")).isEqualTo(OrdersRoute())
        assertThat(routeForSiteLink("/profile#invite")).isEqualTo(InviteRoute)
        assertThat(routeForSiteLink("/wanted?mine=1")).isEqualTo(WantedRoute())
        assertThat(routeForSiteLink("/seller/order-tracking/ABC123")).isEqualTo(SaleRoute("ABC123"))
        assertThat(routeForSiteLink("/seller-books")).isEqualTo(MyBooksRoute)
        assertThat(routeForSiteLink("/seller-orders")).isEqualTo(SalesRoute)
    }

    @Test
    fun `shared links to a search keep its words and filters`() {
        assertThat(
            routeForSiteLink(
                "/filter?search=%E0%A6%AA%E0%A6%A5%E0%A7%87%E0%A6%B0%20%E0%A6%AA%E0%A6%BE%E0%A6%81%E0%A6%9A%E0%A6%BE%E0%A6%B2%E0%A7%80",
            ),
        )
            .isEqualTo(CatalogueRoute(search = "পথের পাঁচালী"))
        assertThat(
            routeForSiteLink("/filter?deals=1&sort=dealPercent"),
        ).isEqualTo(CatalogueRoute(dealsOnly = true, sort = CatalogueSort.DealPercent))
        assertThat(routeForSiteLink("/filter?maxPrice=300&sort=priceLowHigh"))
            .isEqualTo(CatalogueRoute(maxPrice = 300, sort = CatalogueSort.PriceLowHigh))
        assertThat(routeForSiteLink("/filter?bookType=old&category=novels&inStock=1"))
            .isEqualTo(CatalogueRoute(type = BookType.Old, category = "novels", inStockOnly = true))
        assertThat(routeForSiteLink("/filter?sort=nonsense&maxPrice=-5")).isEqualTo(CatalogueRoute())
    }

    @Test
    fun `links that fill a form in carry their words`() {
        assertThat(routeForSiteLink("/wanted?ask=Aparajito")).isEqualTo(WantedRoute(ask = "Aparajito"))
        assertThat(routeForSiteLink("/add-book?title=Aparajito&author=Bibhutibhushan%20Bandyopadhyay&isbn=9789849044055"))
            .isEqualTo(ListBookRoute("Aparajito", "Bibhutibhushan Bandyopadhyay", "9789849044055"))
        assertThat(routeForSiteLink("/")).isEqualTo(HomeRoute)
        assertThat(routeForSiteLink("/cart/")).isEqualTo(CartRoute)
    }

    @Test
    fun `pages the app does not have are left to the website`() {
        assertThat(routeForSiteLink("/privacy")).isNull()
        assertThat(routeForSiteLink("/book")).isNull()
    }

    @Test
    fun `the administrators' pages are left to the website`() {
        assertThat(routeForSiteLink("/admin/returns")).isNull()
        assertThat(routeForSiteLink("/admin/order-tracking/ABC123")).isNull()
    }
}
