package io.github.utshabasak.bookstorebd.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import io.github.utshabasak.bookstorebd.R
import io.github.utshabasak.bookstorebd.core.ui.navigation.AccountRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CartRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CatalogueRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.ChatRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CheckoutRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.EditProfileRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.HomeRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.ListBookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrderPlacedRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.ResetPasswordRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.ReturnRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignInRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignUpRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.WishlistRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.WriteReviewRoute

/** The five tabs, each with its own back stack. */
enum class TopLevel(
    val root: NavKey,
    val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Home(HomeRoute, R.string.tab_home, Icons.Outlined.Home, Icons.Filled.Home),
    Browse(CatalogueRoute(), R.string.tab_browse, Icons.Outlined.Search, Icons.Filled.Search),
    Wishlist(WishlistRoute, R.string.tab_wishlist, Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    Cart(CartRoute, R.string.tab_cart, Icons.Outlined.ShoppingCart, Icons.Filled.ShoppingCart),
    Account(AccountRoute, R.string.tab_account, Icons.Outlined.Person, Icons.Filled.Person),
}

/**
 * The app's navigation: a back stack per tab, kept while the person moves
 * between tabs and across the app being stopped in the background. Back pops
 * the tab's stack; at a tab's root it returns to Home, and at Home's root it
 * leaves the app.
 */
@Stable
class AppNavigationState(
    val stacks: Map<TopLevel, NavBackStack<NavKey>>,
    tab: MutableState<TopLevel>,
) : Navigator {
    var currentTab by tab
        private set

    /** The window shows a list and the item chosen from it side by side (AppPanes). */
    var twoPane by mutableStateOf(false)

    val backStack: NavBackStack<NavKey> get() = stacks.getValue(currentTab)

    /**
     * Whether the tabs show: screens that need the whole window have none
     * beside or under them, such as a form. A conversation needs the whole
     * window only on a phone held upright ([wide] false), where the keyboard
     * leaves little room.
     */
    fun showTabs(wide: Boolean): Boolean =
        backStack.lastOrNull()?.let { top ->
            top !in FULL_SCREEN && top::class !in FULL_SCREEN_TYPES && !(top is ChatRoute && !wide)
        } ?: true

    fun selectTab(tab: TopLevel) {
        if (tab == currentTab) {
            // Tapping the current tab again returns to its first screen.
            stacks.getValue(tab).let { stack -> while (stack.size > 1) stack.removeAt(stack.lastIndex) }
        }
        currentTab = tab
    }

    override fun navigate(route: NavKey) {
        val tab = TopLevel.entries.firstOrNull { it.root == route }
        if (tab != null) {
            selectTab(tab)
            return
        }
        // The screen already shown (a link or a shortcut to where the person is) stays as it is.
        if (backStack.lastOrNull() == route) return
        if (twoPane && replacesItemBesideList(route)) backStack.removeAt(backStack.lastIndex)
        backStack.add(route)
    }

    /** The item open beside [list] while the window shows them side by side; null otherwise. */
    fun openBeside(list: NavKey): NavKey? {
        if (!twoPane) return null
        val stack = stacks.values.firstOrNull { list in it } ?: return null
        return stack.getOrNull(stack.indexOf(list) + 1)?.takeIf { AppPanes.belongsTo(it, list) }
    }

    /** Beside a list, another of its items takes the place of the one shown, so back returns to the list. */
    private fun replacesItemBesideList(route: NavKey): Boolean {
        val top = backStack.lastOrNull() ?: return false
        val below = backStack.getOrNull(backStack.lastIndex - 1) ?: return false
        return AppPanes.isList(below) && AppPanes.sameDetail(top, route)
    }

    override fun back() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        } else if (currentTab != TopLevel.Home) {
            currentTab = TopLevel.Home
        }
    }

    override fun replace(route: NavKey) {
        val tab = TopLevel.entries.firstOrNull { it.root == route }
        if (tab != null) {
            // Done with this flow (an order placed): the tab starts afresh, and the destination tab shows.
            backStack.let { stack -> while (stack.size > 1) stack.removeAt(stack.lastIndex) }
            stacks.getValue(tab).let { stack -> while (stack.size > 1) stack.removeAt(stack.lastIndex) }
            currentTab = tab
            return
        }
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        backStack.add(route)
    }

    private companion object {
        val FULL_SCREEN: Set<NavKey> = setOf(SignInRoute, SignUpRoute, ResetPasswordRoute, CheckoutRoute)
        val FULL_SCREEN_TYPES =
            setOf(
                OrderPlacedRoute::class,
                EditProfileRoute::class,
                ReturnRoute::class,
                WriteReviewRoute::class,
                ListBookRoute::class,
            )
    }
}

@Composable
fun rememberAppNavigationState(): AppNavigationState {
    val stacks = TopLevel.entries.associateWith { rememberNavBackStack(it.root) }
    val tab = rememberSaveable { mutableStateOf(TopLevel.Home) }
    return remember { AppNavigationState(stacks, tab) }
}
