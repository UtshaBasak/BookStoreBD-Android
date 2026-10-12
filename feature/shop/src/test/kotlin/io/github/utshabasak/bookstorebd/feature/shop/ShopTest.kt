package io.github.utshabasak.bookstorebd.feature.shop

import androidx.paging.PagingData
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.repository.CartRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CatalogueRepository
import io.github.utshabasak.bookstorebd.core.data.repository.WishlistRepository
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.CatalogueQuery
import io.github.utshabasak.bookstorebd.core.model.CatalogueSort
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ShopTest {
    @get:Rule val main = MainDispatcherRule()

    private val wishlist = mockk<WishlistRepository>(relaxed = true) { every { ids } returns flowOf(emptySet()) }
    private val cart = mockk<CartRepository>(relaxed = true) { every { quantities } returns flowOf(emptyMap()) }

    @Test
    fun `lists the seller's books as asked, and reports a shop that cannot be loaded`() =
        runTest {
            val catalogue =
                mockk<CatalogueRepository> {
                    every { shop("nusrat") } returns flowOf(null)
                    every { search(any()) } returns flowOf(PagingData.empty())
                    coEvery { refreshShop("nusrat") } returns Outcome.Failure(AppError.Offline)
                }

            val vm = ShopViewModel("nusrat", catalogue, mockk(relaxed = true), mockk(relaxed = true), wishlist, cart)

            vm.error.test { assertThat(expectMostRecentItem()).isEqualTo(AppError.Offline) }
            vm.books.test {
                awaitItem()
                vm.setSort(CatalogueSort.PriceLowHigh)
                vm.setInStockOnly(true)
                cancelAndIgnoreRemainingEvents()
            }
            verify { catalogue.search(CatalogueQuery(seller = "nusrat", sort = CatalogueSort.Relevant)) }
            verify { catalogue.search(CatalogueQuery(seller = "nusrat", sort = CatalogueSort.PriceLowHigh, inStockOnly = true)) }
        }
}
