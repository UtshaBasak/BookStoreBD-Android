package io.github.utshabasak.bookstorebd.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.repository.CatalogueRepository
import io.github.utshabasak.bookstorebd.core.data.repository.WishlistRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.HomeSections
import io.github.utshabasak.bookstorebd.core.model.Outcome
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

@RunWith(RobolectricTestRunner::class)
class HomeTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val compose = createComposeRule()

    private val deal =
        Book(id = "b1", title = "দেয়াল", author = "হুমায়ূন আহমেদ", price = 450.0, salePrice = 360.0, discountPercent = 20, stock = 3)
    private val sections = MutableStateFlow<HomeSections?>(null)
    private val catalogue =
        mockk<CatalogueRepository>(relaxed = true) {
            every { homeSections } returns sections
            every { recentlyViewed } returns flowOf(emptyList())
            every { recentlyViewedIds } returns flowOf(emptyList())
        }
    private val wishlist = mockk<WishlistRepository>(relaxed = true) { every { ids } returns flowOf(emptySet()) }
    private val session = mockk<UserSession>(relaxed = true)

    @Test
    fun `shows the saved shelves even when the refresh fails, with the reason`() =
        runTest {
            sections.value = HomeSections(deals = listOf(deal))
            coEvery { catalogue.refreshHome() } returns Outcome.Failure(AppError.Offline)

            val viewModel = HomeViewModel(catalogue, wishlist, session)

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
            val viewModel = HomeViewModel(catalogue, wishlist, session)

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
        compose.onNodeWithText("Quick deals").assertIsDisplayed()
        compose.onNodeWithText("দেয়াল").performClick()
        assertThat(opened).isEqualTo(deal)
    }
}
