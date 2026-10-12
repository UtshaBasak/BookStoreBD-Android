package io.github.utshabasak.bookstorebd.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.inbox.ChatRepository
import io.github.utshabasak.bookstorebd.core.data.inbox.NotificationRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CartRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CatalogueRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.WishlistRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.CategoryCount
import io.github.utshabasak.bookstorebd.core.model.HomeSections
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.TopPicks
import io.github.utshabasak.bookstorebd.core.testing.MainDispatcherRule
import io.github.utshabasak.bookstorebd.core.ui.navigation.CatalogueRoute
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
class HomeTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val compose = createComposeRule()

    private val deal =
        Book(id = "b1", title = "দেয়াল", author = "হুমায়ূন আহমেদ", price = 450.0, salePrice = 360.0, discountPercent = 20, stock = 3)
    private val pick = deal.copy(id = "b2", title = "Aparajito")
    private val sections = MutableStateFlow<HomeSections?>(null)
    private val catalogue =
        mockk<CatalogueRepository>(relaxed = true) {
            every { homeSections } returns sections
            every { recentlyViewed } returns flowOf(emptyList())
            every { recentlyViewedIds } returns flowOf(emptyList())
            every { latestBooks } returns flowOf(emptyList())
            every { topPicks } returns MutableStateFlow(null)
        }
    private val wishlist = mockk<WishlistRepository>(relaxed = true) { every { ids } returns flowOf(emptySet()) }
    private val cart = mockk<CartRepository>(relaxed = true) { every { quantities } returns flowOf(emptyMap()) }
    private val session = mockk<UserSession>(relaxed = true) { every { user } returns flowOf(null) }
    private val notifications = mockk<NotificationRepository>(relaxed = true) { every { unread } returns MutableStateFlow(0) }
    private val chats = mockk<ChatRepository>(relaxed = true) { every { unread } returns MutableStateFlow(0) }
    private val config = mockk<ConfigRepository>(relaxed = true) { every { config } returns flowOf(null) }

    @Test
    fun `shows the saved shelves even when the refresh fails, with the reason`() =
        runTest {
            sections.value = HomeSections(deals = listOf(deal))
            coEvery { catalogue.refreshHome() } returns Outcome.Failure(AppError.Offline)

            val viewModel = HomeViewModel(catalogue, wishlist, cart, session, notifications, chats, config)

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertThat(state.sections?.deals).containsExactly(deal)
                assertThat(state.error).isEqualTo(AppError.Offline)
                assertThat(state.isRefreshing).isFalse()
            }
        }

    @Test
    fun `asks a signed-out shopper to sign in before saving a book`() =
        runTest {
            coEvery { session.currentUser() } returns null
            val viewModel = HomeViewModel(catalogue, wishlist, cart, session, notifications, chats, config)

            viewModel.eventFlow.test {
                viewModel.toggleWishlist(deal)
                assertThat(awaitItem()).isEqualTo(HomeEvent.SignInToSave)
            }
            coVerify(exactly = 0) { wishlist.add(any()) }
        }

    @Test
    fun `a first load that fails offers to try again`() {
        var retried = false
        compose.setContent {
            BookStoreTheme {
                HomeScreen(
                    state = HomeUiState(error = AppError.Offline),
                    onRefresh = { retried = true },
                    onSearch = {},
                    onOpenBook = {},
                    onToggleWishlist = {},
                    onOpenCatalogue = {},
                )
            }
        }
        compose.onNodeWithText("No connection").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertThat(retried).isTrue()
    }

    @Test
    @Config(qualifiers = "w411dp-h1400dp")
    fun `lists the shelves with their books`() {
        var opened: Book? = null
        compose.setContent {
            BookStoreTheme {
                HomeScreen(
                    state = HomeUiState(sections = HomeSections(deals = listOf(deal))),
                    onRefresh = {},
                    onSearch = {},
                    onOpenBook = { opened = it },
                    onToggleWishlist = {},
                    onOpenCatalogue = {},
                )
            }
        }
        scrollTo("Discounts from our sellers, the biggest first.")
        compose.onNodeWithText("দেয়াল").performClick()
        assertThat(opened).isEqualTo(deal)
    }

    @Test
    fun `top picks say whether they come from the shopper's own history`() {
        compose.setContent {
            BookStoreTheme {
                HomeScreen(
                    state = HomeUiState(sections = HomeSections(), topPicks = TopPicks(listOf(pick), personal = true)),
                    onRefresh = {},
                    onSearch = {},
                    onOpenBook = {},
                    onToggleWishlist = {},
                    onOpenCatalogue = {},
                )
            }
        }
        scrollTo("Chosen from what you have bought, saved and looked at.")
        compose.onNodeWithText("Top picks for you").assertIsDisplayed()
        compose.onNodeWithText("Aparajito").assertIsDisplayed()
    }

    @Test
    fun `opens with the shop's quick links, the hero, and an invitation to sell while there are no books`() {
        var sold = 0
        var opened: CatalogueRoute? = null
        compose.setContent {
            BookStoreTheme {
                HomeScreen(
                    state = HomeUiState(sections = HomeSections(), isRefreshing = false),
                    onRefresh = {},
                    onSearch = {},
                    onOpenBook = {},
                    onToggleWishlist = {},
                    onOpenCatalogue = { opened = it },
                    onSell = { sold++ },
                )
            }
        }
        compose.onNodeWithText("Second-hand").performClick()
        assertThat(opened).isEqualTo(CatalogueRoute(type = BookType.Old))
        compose.onNodeWithText("Sell a book").performClick()
        scrollTo("No books on the shelf yet.")
        scrollTo("List a book")
        compose.onNodeWithText("List a book").performClick()
        assertThat(sold).isEqualTo(2)
    }

    @Test
    fun `category tiles put the counted categories first, then the starter ones, without repeating a name`() {
        val tiles = categoryTiles(listOf(CategoryCount("Poetry", 4), CategoryCount("Physics", 2)))

        assertThat(tiles.take(2).map { it.name }).containsExactly("Poetry", "Physics").inOrder()
        assertThat(tiles.map { it.name }).containsNoDuplicates()
        assertThat(tiles.map { it.name }).contains("Novels")
        assertThat(tiles.first { it.name == "Novels" }.books).isEqualTo(0)
    }

    private fun scrollTo(text: String) {
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }
}
