package io.github.utshabasak.bookstorebd.feature.wishlist

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.repository.CartRepository
import io.github.utshabasak.bookstorebd.core.data.repository.RestockRequests
import io.github.utshabasak.bookstorebd.core.data.repository.SettingsRepository
import io.github.utshabasak.bookstorebd.core.data.repository.WishlistRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WishlistTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val compose = createComposeRule()

    private val settings = mockk<SettingsRepository>(relaxed = true) { every { wishlistSort } returns flowOf(null) }

    @Test
    fun `sorts the wishlist the way the website does`() {
        val cheap = book.copy(id = "a", title = "zebra", price = 100.0, salePrice = 100.0, stock = 0)
        val deal = book.copy(id = "b", title = "Apple", price = 500.0, salePrice = 400.0, discountPercent = 20)
        val saved = listOf(cheap, deal, book)

        assertThat(WishlistSort.Recent.apply(saved)).containsExactly(book, deal, cheap).inOrder()
        assertThat(WishlistSort.InStock.apply(saved).last()).isEqualTo(cheap)
        assertThat(WishlistSort.Deals.apply(saved).first()).isEqualTo(deal)
        assertThat(WishlistSort.PriceHigh.apply(saved).first()).isEqualTo(deal)
        assertThat(WishlistSort.Title.apply(saved).map { it.id }).containsExactly("b", "b1", "a").inOrder()
        assertThat(WishlistSort.from("nonsense")).isEqualTo(WishlistSort.Recent)
    }

    private val book = Book(id = "b1", title = "Pather Panchali", author = "Bibhutibhushan", price = 300.0, stock = 2)

    @Test
    fun `the cart button puts a book in the cart and leaves it on the wishlist, as on the website`() =
        runTest {
            val wishlist = mockk<WishlistRepository>(relaxed = true) { every { wishlist } returns flowOf(listOf(book)) }
            val cart =
                mockk<CartRepository> {
                    every { quantities } returns flowOf(emptyMap())
                    coEvery { toggle("b1", false) } returns Outcome.Success(Unit)
                }
            val session = mockk<UserSession> { every { user } returns flowOf(null) }
            val restock = mockk<RestockRequests>(relaxed = true) { every { requested } returns MutableStateFlow(emptyMap()) }
            val vm = WishlistViewModel(wishlist, cart, settings, session, restock)

            vm.eventFlow.test {
                vm.toggleCart(book)
                assertThat(awaitItem()).isEqualTo(WishlistEvent.AddedToCart)
            }
            coVerify(exactly = 0) { wishlist.remove(any()) }
        }

    @Test
    fun `a sold-out book offers to notify instead of the cart, and the list is counted`() {
        var asked: Book? = null
        val soldOut = book.copy(id = "b9", stock = 0)
        compose.setContent {
            BookStoreTheme {
                WishlistScreen(
                    state = WishlistUiState(signedIn = true, books = listOf(book, soldOut), requested = mapOf("b9" to false)),
                    onRefresh = {},
                    onOpenBook = {},
                    onRemove = {},
                    onToggleCart = {},
                    onToggleRequest = { asked = it },
                    onSignIn = {},
                    onBrowse = {},
                )
            }
        }
        compose.onNodeWithText("2 saved books").assertIsDisplayed()
        compose.onNodeWithText("Add to cart").assertIsDisplayed()
        compose.onNodeWithText("Notify me when it is back").performClick()
        assertThat(asked).isEqualTo(soldOut)
    }

    @Test
    fun `one's own sold-out book has nobody to ask, and a book not yet checked can still be asked for`() {
        var asked: Book? = null
        val mine = book.copy(id = "b8", stock = 0, sellerEmail = "me@example.com")
        val unchecked = book.copy(id = "b9", stock = 0, sellerEmail = "other@example.com")
        compose.setContent {
            BookStoreTheme {
                WishlistScreen(
                    state = WishlistUiState(signedIn = true, books = listOf(mine, unchecked), viewerEmail = "me@example.com"),
                    onRefresh = {},
                    onOpenBook = {},
                    onRemove = {},
                    onToggleCart = {},
                    onToggleRequest = { asked = it },
                    onSignIn = {},
                    onBrowse = {},
                )
            }
        }
        compose.onAllNodesWithText("Notify me when it is back").assertCountEquals(1)
        compose.onNodeWithText("Notify me when it is back").performClick()
        assertThat(asked).isEqualTo(unchecked)
    }

    @Test
    fun `an empty wishlist explains how to fill it`() {
        compose.setContent {
            BookStoreTheme {
                WishlistScreen(
                    state = WishlistUiState(signedIn = true),
                    onRefresh = {},
                    onOpenBook = {},
                    onRemove = {},
                    onToggleCart = {},
                    onSignIn = {},
                    onBrowse = {},
                )
            }
        }
        compose.onNodeWithText("Your wishlist is empty").assertIsDisplayed()
        compose.onNodeWithText("Browse books").assertIsDisplayed()
    }
}
