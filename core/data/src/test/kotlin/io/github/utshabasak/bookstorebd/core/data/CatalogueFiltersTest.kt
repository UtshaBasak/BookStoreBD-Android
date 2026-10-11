package io.github.utshabasak.bookstorebd.core.data

import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.repository.CataloguePagingSource
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.CatalogueQuery
import io.github.utshabasak.bookstorebd.core.model.CatalogueSort
import org.junit.Test

class CatalogueFiltersTest {
    @Test
    fun `an empty search asks for everything, most relevant first`() {
        assertThat(CataloguePagingSource.filters(CatalogueQuery(), page = 1, pageSize = 20))
            .containsExactly("sort", "relevant", "page", "1", "pageSize", "20")
    }

    @Test
    fun `every filter becomes the parameter the API names`() {
        val query =
            CatalogueQuery(
                search = "  পথের পাঁচালী ",
                type = BookType.Old,
                condition = "good",
                minPrice = 100,
                maxPrice = 500,
                minRating = 4,
                inStockOnly = true,
                dealsOnly = true,
                seller = "nusrat",
                sort = CatalogueSort.PriceLowHigh,
            )
        assertThat(CataloguePagingSource.filters(query, page = 3, pageSize = 500)).isEqualTo(
            mapOf(
                "search" to "পথের পাঁচালী",
                "bookType" to "old",
                "condition" to "good",
                "minPrice" to "100",
                "maxPrice" to "500",
                "rating" to "4",
                "inStock" to "1",
                "deals" to "1",
                "seller" to "nusrat",
                "sort" to "priceLowHigh",
                "page" to "3",
                "pageSize" to "50",
            ),
        )
    }

    @Test
    fun `counts the filters that are on, for the badge`() {
        assertThat(CatalogueQuery(search = "x", sort = CatalogueSort.Newest).activeFilterCount).isEqualTo(0)
        assertThat(CatalogueQuery(categories = setOf("Poetry"), minPrice = 1, inStockOnly = true).activeFilterCount).isEqualTo(3)
    }
}
