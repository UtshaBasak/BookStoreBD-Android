package io.github.utshabasak.bookstorebd.feature.book

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.repository.BookRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CartRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CatalogueRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CommunityRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.WishlistRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.BookDetail
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.SessionUser
import io.github.utshabasak.bookstorebd.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class BookTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val compose = createComposeRule()

    private val book = Book(id = "b1", title = "দেয়াল", author = "হুমায়ূন আহমেদ", price = 450.0, stock = 3, sellerEmail = "s@example.com")
    private val books =
        mockk<BookRepository>(relaxed = true) {
            every { book("b1") } returns flowOf(BookDetail(book, emptyList()))
            coEvery { refreshBook("b1") } returns Outcome.Success(Unit)
        }
    private val cart = mockk<CartRepository>(relaxed = true)
    private val wishlist = mockk<WishlistRepository>(relaxed = true) { every { ids } returns flowOf(emptySet()) }
    private val session = mockk<UserSession>(relaxed = true) { every { user } returns flowOf(null) }
    private val catalogue = mockk<CatalogueRepository>(relaxed = true)
    private val config = mockk<ConfigRepository> { every { config } returns flowOf(null) }
    private val community = mockk<CommunityRepository>(relaxed = true)

    private fun viewModel() = BookViewModel("b1", books, cart, wishlist, session, catalogue, config, community)

    @Test
    fun `records the view and adds the chosen number of copies`() =
        runTest {
            coEvery { session.currentUser() } returns SessionUser("u1", "nusrat", "n@example.com", false)
            coEvery { cart.add("b1", 2) } returns Outcome.Success(Unit)
            val vm = viewModel()

            vm.setQuantity(2)
            vm.eventFlow.test {
                vm.addToCart()
                assertThat(awaitItem()).isEqualTo(BookEvent.AddedToCart)
            }
            coVerify { catalogue.recordView("b1") }
            coVerify { cart.add("b1", 2) }
        }

    @Test
    fun `keeps the quantity within what is in stock`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                skipItems(1)
                vm.setQuantity(10)
                assertThat(expectMostRecentItem().quantity).isEqualTo(3)
            }
        }

    @Test
    fun `asks a signed-out shopper to sign in instead of adding to the cart`() =
        runTest {
            coEvery { session.currentUser() } returns null
            val vm = viewModel()
            vm.eventFlow.test {
                vm.addToCart()
                assertThat(awaitItem()).isEqualTo(BookEvent.SignInRequired)
            }
            coVerify(exactly = 0) { cart.add(any(), any()) }
        }

    @Test
    fun `a sold-out book offers to notify instead of the cart`() {
        var requested = false
        compose.setContent {
            BookStoreTheme {
                BookScreen(
                    state = BookUiState(detail = BookDetail(book.copy(stock = 0), emptyList()), loading = false),
                    onBack = {},
                    onRetry = {},
                    onQuantity = {},
                    onAddToCart = {},
                    onToggleWishlist = {},
                    onToggleRequest = { requested = true },
                    onShare = {},
                    onOpen = {},
                )
            }
        }
        compose.onNodeWithText("Sold out").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Notify me when it is back").performScrollTo().performClick()
        assertThat(requested).isTrue()
    }

    @Test
    fun `a book that cannot be loaded offers to try again`() {
        compose.setContent {
            BookStoreTheme {
                BookScreen(
                    state = BookUiState(loading = false, error = AppError.Timeout),
                    onBack = {},
                    onRetry = {},
                    onQuantity = {},
                    onAddToCart = {},
                    onToggleWishlist = {},
                    onToggleRequest = {},
                    onShare = {},
                    onOpen = {},
                )
            }
        }
        compose.onNodeWithText("The shop is waking up").assertIsDisplayed()
    }
}
