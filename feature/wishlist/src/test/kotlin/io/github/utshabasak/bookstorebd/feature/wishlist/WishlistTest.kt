package io.github.utshabasak.bookstorebd.feature.wishlist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.repository.CartRepository
import io.github.utshabasak.bookstorebd.core.data.repository.SettingsRepository
import io.github.utshabasak.bookstorebd.core.data.repository.WishlistRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
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
    fun `moving a book to the cart adds it first, then takes it off the wishlist`() =
        runTest {
            val wishlist = mockk<WishlistRepository>(relaxed = true) { every { wishlist } returns flowOf(listOf(book)) }
            val cart = mockk<CartRepository> { coEvery { add("b1") } returns Outcome.Success(Unit) }
            val session = mockk<UserSession> { every { user } returns flowOf(null) }
            val vm = WishlistViewModel(wishlist, cart, settings, session)

            vm.eventFlow.test {
                vm.moveToCart(book)
                assertThat(awaitItem()).isEqualTo(WishlistEvent.MovedToCart)
            }
            coVerifyOrder {
                cart.add("b1")
                wishlist.remove("b1")
            }
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
                    onMoveToCart = {},
                    onSignIn = {},
                    onBrowse = {},
                )
            }
        }
        compose.onNodeWithText("Your wishlist is empty").assertIsDisplayed()
        compose.onNodeWithText("Browse books").assertIsDisplayed()
    }
}
