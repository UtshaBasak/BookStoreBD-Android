package io.github.utshabasak.bookstorebd.core.model

/** How the catalogue may be ordered, by the API's own names. */
enum class CatalogueSort(
    val apiValue: String,
) {
    Relevant("relevant"),
    Newest("newest"),
    Rated("rated"),
    PriceLowHigh("priceLowHigh"),
    PriceHighLow("priceHighLow"),
    DealPercent("dealPercent"),
    DealAmount("dealAmount"),
}

/** One search of the catalogue: words and filters. Empty means everything. */
data class CatalogueQuery(
    val search: String = "",
    val type: BookType? = null,
    val condition: String? = null,
    val categories: Set<String> = emptySet(),
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
    /** A floor: 4 means four stars and up. */
    val minRating: Int? = null,
    val inStockOnly: Boolean = false,
    val dealsOnly: Boolean = false,
    /** One seller's books, by username. */
    val seller: String? = null,
    val sort: CatalogueSort = CatalogueSort.Relevant,
) {
    /** How many filters are on, for the badge on the filter button. Words and sort are not filters. */
    val activeFilterCount: Int
        get() =
            listOf(
                type != null,
                condition != null,
                categories.isNotEmpty(),
                minPrice != null || maxPrice != null,
                minRating != null,
                inStockOnly,
                dealsOnly,
            ).count { it }
}

/** One page of results, with enough to know whether there are more. */
data class CataloguePage(
    val items: List<Book>,
    val total: Int,
    val page: Int,
    val pageCount: Int,
)

/** Every shelf on the home screen, from one request. */
data class HomeSections(
    val deals: List<Book> = emptyList(),
    val trending: List<Book> = emptyList(),
    val popular: List<Book> = emptyList(),
    val bestsellers: List<Book> = emptyList(),
    val topRated: List<Book> = emptyList(),
    val budget: List<Book> = emptyList(),
    val discover: List<Book> = emptyList(),
    val writers: List<PopularWriter> = emptyList(),
    val categories: List<CategoryCount> = emptyList(),
)

/** Top picks: [personal] when they come from the person's own history, otherwise the best-rated books in stock. */
data class TopPicks(
    val books: List<Book>,
    val personal: Boolean,
)

data class PopularWriter(
    val name: String,
    val books: Int,
    val sold: Int,
)

data class CategoryCount(
    val name: String,
    val books: Int,
)

/** What the search box suggests as someone types. */
data class Suggestions(
    val books: List<SuggestedBook> = emptyList(),
    val authors: List<CategoryCount> = emptyList(),
    val categories: List<CategoryCount> = emptyList(),
    val sellers: List<SellerHit> = emptyList(),
) {
    val isEmpty: Boolean get() = books.isEmpty() && authors.isEmpty() && categories.isEmpty() && sellers.isEmpty()
}

data class SuggestedBook(
    val id: String,
    val title: String,
    val author: String,
    val cover: String?,
    val price: Double,
    val salePrice: Double,
    val discountPercent: Int,
    val inStock: Boolean,
)

data class SellerHit(
    val username: String,
    val avatar: String?,
    val books: Int,
)

/** A seller's shop front. */
data class SellerShop(
    val id: String,
    val username: String,
    val email: String,
    val profilePicture: String?,
    val banner: String?,
    val joinedAt: String?,
    val books: Int,
    val inStock: Int,
    val sold: Int,
    val ratingAverage: Double,
    val ratingCount: Int,
    val sellerRatingAverage: Double,
    val sellerRatingCount: Int,
)

/** The seller named on a listing, for the link to their shop. */
data class SellerSummary(
    val username: String,
    val picture: String?,
    val ratingAverage: Double,
    val ratingCount: Int,
)
