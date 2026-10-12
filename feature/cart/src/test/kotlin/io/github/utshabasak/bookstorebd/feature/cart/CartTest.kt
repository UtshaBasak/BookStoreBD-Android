package io.github.utshabasak.bookstorebd.feature.cart

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.repository.CartRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.RestockRequests
import io.github.utshabasak.bookstorebd.core.data.repository.WishlistRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.CartItem
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.SessionUser
import io.github.utshabasak.bookstorebd.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CartTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val compose = createComposeRule()

    private val book =
        Book(id = "b1", title = "দেয়াল", author = "হুমায়ূন আহমেদ", price = 450.0, salePrice = 400.0, discountPercent = 11, stock = 5)
    private val item = CartItem(book, quantity = 2)
    private val wishlist = mockk<WishlistRepository>(relaxed = true) { every { ids } returns flowOf(emptySet()) }
    private val restock = mockk<RestockRequests>(relaxed = true) { every { requested } returns MutableStateFlow(emptyMap()) }

    @Test
    fun `totals the cart and changes a quantity through the server`() =
        runTest {
            val cart =
                mockk<CartRepository>(relaxed = true) {
                    every { cart } returns flowOf(listOf(item))
                    coEvery { setQuantity("b1", 3) } returns Outcome.Success(Unit)
                }
            val session = mockk<UserSession> { every { user } returns flowOf(SessionUser("u1", "n", "n@example.com", false)) }
            val config = mockk<ConfigRepository> { every { config } returns flowOf(null) }
            val vm = CartViewModel(cart, session, config, wishlist, restock)

            vm.uiState.test {
                val state = expectMostRecentItem()
                assertThat(state.booksTotal).isEqualTo(800.0)
                assertThat(state.canCheckOut).isTrue()
                vm.setQuantity(item, 3)
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { cart.setQuantity("b1", 3) }
        }

    @Test
    fun `a sold-out book stays in the cart but is left out of the total and of checkout`() {
        val state = CartUiState(signedIn = true, items = listOf(item, CartItem(book.copy(id = "b2", stock = 0), 1)))

        assertThat(state.booksTotal).isEqualTo(800.0)
        assertThat(state.canCheckOut).isTrue()
        assertThat(state.hasSoldOut).isTrue()
    }

    @Test
    fun `with nothing in stock there is nothing to check out, and a sold-out book offers to notify`() {
        var asked = false
        compose.setContent {
            BookStoreTheme {
                CartScreen(
                    state =
                        CartUiState(
                            signedIn = true,
                            items = listOf(CartItem(book.copy(stock = 0), 1)),
                            requested = mapOf("b1" to false),
                        ),
                    onToggleRequest = { asked = true },
                    onRefresh = {},
                    onQuantity = { _, _ -> },
                    onRemove = {},
                    onOpenBook = {},
                    onCheckout = {},
                    onSignIn = {},
                    onBrowse = {},
                )
            }
        }
        compose.onNodeWithText("Nothing in stock to check out").assertIsNotEnabled()
        compose.onNodeWithText("Sold out. It stays here, and you can order it when it is back.").assertIsDisplayed()
        compose.onNodeWithText("Notify me when it is back").performClick()
        assertThat(asked).isTrue()
    }

    @Test
    fun `the cart says how many books wait, and offers to keep shopping`() {
        var browsed = false
        compose.setContent {
            BookStoreTheme {
                CartScreen(
                    state = CartUiState(signedIn = true, items = listOf(CartItem(book, 2))),
                    onRefresh = {},
                    onQuantity = { _, _ -> },
                    onRemove = {},
                    onOpenBook = {},
                    onCheckout = {},
                    onSignIn = {},
                    onBrowse = { browsed = true },
                )
            }
        }
        compose.onNodeWithText("1 book waiting for you").assertIsDisplayed()
        compose.onNodeWithText("Continue shopping").performClick()
        assertThat(browsed).isTrue()
    }

    @Test
    fun `a signed-out shopper is asked to sign in`() {
        var signIn = false
        compose.setContent {
            BookStoreTheme {
                CartScreen(
                    state = CartUiState(signedIn = false),
                    onRefresh = {},
                    onQuantity = { _, _ -> },
                    onRemove = {},
                    onOpenBook = {},
                    onCheckout = {},
                    onSignIn = { signIn = true },
                    onBrowse = {},
                )
            }
        }
        compose.onNodeWithText("Sign in").performClick()
        assertThat(signIn).isTrue()
    }
}
