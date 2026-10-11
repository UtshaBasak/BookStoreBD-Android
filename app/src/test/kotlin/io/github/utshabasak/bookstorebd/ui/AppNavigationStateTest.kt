package io.github.utshabasak.bookstorebd.ui

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CartRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CatalogueRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.ChatRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CheckoutRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.HomeRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.MessagesRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrderPlacedRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrderRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrdersRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignInRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationStateTest {
    private fun state(): AppNavigationState =
        AppNavigationState(
            stacks = TopLevel.entries.associateWith { NavBackStack(it.root) },
            tab = mutableStateOf(TopLevel.Home),
        )

    @Test
    fun `a screen opens on the current tab's stack`() {
        val navigation = state()

        navigation.navigate(BookRoute("b1"))

        assertEquals(listOf<NavKey>(HomeRoute, BookRoute("b1")), navigation.backStack.toList())
        assertEquals(listOf<NavKey>(CartRoute), navigation.stacks.getValue(TopLevel.Cart).toList())
    }

    @Test
    fun `a tab's root switches to that tab and keeps the others' stacks`() {
        val navigation = state()
        navigation.navigate(BookRoute("b1"))

        navigation.navigate(CartRoute)

        assertEquals(TopLevel.Cart, navigation.currentTab)
        assertEquals(listOf<NavKey>(HomeRoute, BookRoute("b1")), navigation.stacks.getValue(TopLevel.Home).toList())
    }

    @Test
    fun `tapping the current tab again returns to its first screen`() {
        val navigation = state()
        navigation.navigate(BookRoute("b1"))
        navigation.navigate(BookRoute("b2"))

        navigation.selectTab(TopLevel.Home)

        assertEquals(listOf<NavKey>(HomeRoute), navigation.backStack.toList())
    }

    @Test
    fun `back pops the stack, then returns to Home from another tab's root`() {
        val navigation = state()
        navigation.selectTab(TopLevel.Browse)
        navigation.navigate(BookRoute("b1"))

        navigation.back()
        assertEquals(listOf<NavKey>(CatalogueRoute()), navigation.backStack.toList())

        navigation.back()
        assertEquals(TopLevel.Home, navigation.currentTab)
    }

    @Test
    fun `finishing a flow resets its tab and shows the destination tab`() {
        val navigation = state()
        navigation.selectTab(TopLevel.Cart)
        navigation.navigate(CheckoutRoute)
        navigation.replace(OrderPlacedRoute(orderNumber = "BS-1", total = 500.0))
        assertEquals(listOf(CartRoute, OrderPlacedRoute("BS-1", 500.0)), navigation.backStack.toList())

        navigation.replace(HomeRoute)

        assertEquals(TopLevel.Home, navigation.currentTab)
        assertEquals(listOf<NavKey>(CartRoute), navigation.stacks.getValue(TopLevel.Cart).toList())
    }

    @Test
    fun `forms and checkout hide the tab bar`() {
        val navigation = state()
        assertTrue(navigation.showTabs(wide = false))

        navigation.navigate(SignInRoute)
        assertFalse(navigation.showTabs(wide = false))
        assertFalse(navigation.showTabs(wide = true))

        navigation.replace(OrderPlacedRoute(orderNumber = "BS-1", total = 500.0))
        assertFalse(navigation.showTabs(wide = false))
    }

    @Test
    fun `a conversation has the whole window on a phone, and keeps the tabs beside it on a wide screen`() {
        val navigation = state()

        navigation.navigate(ChatRoute("seller@example.com"))

        assertFalse(navigation.showTabs(wide = false))
        assertTrue(navigation.showTabs(wide = true))
    }

    @Test
    fun `beside a list, another item replaces the one shown, so back returns to the list`() {
        val navigation = state()
        navigation.twoPane = true
        navigation.navigate(OrdersRoute())
        navigation.navigate(OrderRoute("BS-1"))

        navigation.navigate(OrderRoute("BS-2"))

        assertEquals(listOf(HomeRoute, OrdersRoute(), OrderRoute("BS-2")), navigation.backStack.toList())
        navigation.back()
        assertEquals(listOf(HomeRoute, OrdersRoute()), navigation.backStack.toList())
    }

    @Test
    fun `a link to the screen already shown leaves it as it is`() {
        val navigation = state()
        navigation.navigate(BookRoute("b1"))

        navigation.navigate(BookRoute("b1"))

        assertEquals(listOf(HomeRoute, BookRoute("b1")), navigation.backStack.toList())
    }

    @Test
    fun `on a phone, items open one over another as before`() {
        val navigation = state()
        navigation.navigate(MessagesRoute)
        navigation.navigate(ChatRoute("a@example.com"))

        navigation.navigate(ChatRoute("b@example.com"))

        assertEquals(4, navigation.backStack.size)
    }
}
