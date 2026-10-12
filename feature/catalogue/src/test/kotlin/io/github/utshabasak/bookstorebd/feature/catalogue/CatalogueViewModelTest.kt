package io.github.utshabasak.bookstorebd.feature.catalogue

import androidx.paging.PagingData
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.repository.CartRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CatalogueRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.SettingsRepository
import io.github.utshabasak.bookstorebd.core.data.repository.WishlistRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.CatalogueQuery
import io.github.utshabasak.bookstorebd.core.model.CatalogueSort
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.SellerHit
import io.github.utshabasak.bookstorebd.core.model.Suggestions
import io.github.utshabasak.bookstorebd.core.testing.MainDispatcherRule
import io.github.utshabasak.bookstorebd.core.ui.navigation.CatalogueRoute
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class CatalogueViewModelTest {
    @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())

    private val book = Book(id = "b1", title = "Pather Panchali", author = "Bibhutibhushan", price = 300.0, stock = 1)
    private val queries = mutableListOf<CatalogueQuery>()
    private val catalogue =
        mockk<CatalogueRepository>(relaxed = true) {
            val captured = slot<CatalogueQuery>()
            every { search(capture(captured), any()) } answers {
                queries += captured.captured
                flowOf(PagingData.from(listOf(book)))
            }
            every { recentSearches } returns flowOf(listOf("tagore"))
            coEvery { popularSearches() } returns Outcome.Success(listOf("humayun ahmed"))
        }
    private val wishlist = mockk<WishlistRepository>(relaxed = true) { every { ids } returns flowOf(emptySet()) }
    private val cart = mockk<CartRepository>(relaxed = true) { every { quantities } returns flowOf(emptyMap()) }
    private val session = mockk<UserSession>(relaxed = true)
    private val config = mockk<ConfigRepository> { every { config } returns flowOf(null) }
    private val settings = mockk<SettingsRepository>(relaxed = true) { every { voiceLanguage } returns flowOf(null) }

    private fun viewModel(route: CatalogueRoute = CatalogueRoute()) =
        CatalogueViewModel(
            route,
            catalogue,
            wishlist,
            cart,
            session,
            config,
            settings,
        )

    @Test
    fun `opens with the search or filter it was given`() =
        runTest(main.testDispatcher) {
            val vm = viewModel(CatalogueRoute(category = "Poetry", dealsOnly = true, sort = CatalogueSort.DealPercent))

            vm.results.first()

            val expected = CatalogueQuery(categories = setOf("Poetry"), dealsOnly = true, sort = CatalogueSort.DealPercent)
            assertThat(queries.last()).isEqualTo(expected)
        }

    @Test
    fun `a search keeps the filters and sort, and is remembered`() =
        runTest(main.testDispatcher) {
            val vm = viewModel(CatalogueRoute(sort = CatalogueSort.Newest))
            vm.applyFilters(CatalogueQuery(type = BookType.Old, inStockOnly = true))

            vm.search("  pather panchali ")
            advanceUntilIdle()

            assertThat(
                vm.query.value,
            ).isEqualTo(CatalogueQuery(search = "pather panchali", type = BookType.Old, inStockOnly = true, sort = CatalogueSort.Newest))
            coVerify { catalogue.rememberSearch("pather panchali") }
        }

    @Test
    fun `clearing filters keeps the words and the sort`() =
        runTest(main.testDispatcher) {
            val vm = viewModel(CatalogueRoute(search = "tagore", sort = CatalogueSort.Rated))
            vm.applyFilters(CatalogueQuery(minPrice = 100, categories = setOf("Poetry")))

            vm.clearFilters()

            assertThat(vm.query.value).isEqualTo(CatalogueQuery(search = "tagore", sort = CatalogueSort.Rated))
        }

    @Test
    fun `suggests after a pause in typing, not on every key`() =
        runTest(main.testDispatcher) {
            coEvery { catalogue.suggest(any()) } returns Outcome.Success(Suggestions())
            val vm = viewModel()

            vm.suggestions.test {
                assertThat(awaitItem()).isNull()
                vm.onSearchTextChange("h")
                vm.onSearchTextChange("ha")
                vm.onSearchTextChange("har")
                advanceTimeBy(300)
                assertThat(awaitItem()).isEqualTo(Suggestions())
            }
            coVerify(exactly = 1) { catalogue.suggest("har") }
        }

    @Test
    fun `a category from the suggestions replaces the words`() =
        runTest(main.testDispatcher) {
            val vm = viewModel(CatalogueRoute(search = "poems"))
            vm.filterByCategory("Poetry")
            assertThat(vm.query.value.search).isEmpty()
            assertThat(vm.query.value.categories).containsExactly("Poetry")
        }

    @Test
    fun `says how many books match, and finds the sellers whose name matches`() =
        runTest(main.testDispatcher) {
            val onTotal = slot<(Int) -> Unit>()
            every { catalogue.search(any(), capture(onTotal)) } answers {
                onTotal.captured(42)
                flowOf(PagingData.from(listOf(book)))
            }
            coEvery { catalogue.matchingSellers("nusrat") } returns Outcome.Success(listOf(SellerHit("nusrat", null, 3)))
            val vm = viewModel(CatalogueRoute(search = "nusrat"))
            backgroundScope.launch { vm.sellers.collect {} }

            vm.results.first()
            advanceUntilIdle()

            assertThat(vm.total.value).isEqualTo(42)
            assertThat(vm.sellers.value.map { it.username }).containsExactly("nusrat")
        }

    @Test
    fun `clearing recent searches and choosing the voice language are kept`() =
        runTest(main.testDispatcher) {
            val vm = viewModel()

            vm.clearRecentSearches()
            vm.setVoiceLanguage("bn-BD")
            advanceUntilIdle()

            coVerify { catalogue.clearRecentSearches() }
            coVerify { settings.setVoiceLanguage("bn-BD") }
        }
}
