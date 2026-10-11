package io.github.utshabasak.bookstorebd.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.utshabasak.bookstorebd.core.data.cache.ResponseCache
import io.github.utshabasak.bookstorebd.core.database.RecentSearch
import io.github.utshabasak.bookstorebd.core.database.RecentSearchDao
import io.github.utshabasak.bookstorebd.core.database.RecentlyViewed
import io.github.utshabasak.bookstorebd.core.database.RecentlyViewedDao
import io.github.utshabasak.bookstorebd.core.datastore.SettingsStore
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.CatalogueQuery
import io.github.utshabasak.bookstorebd.core.model.HomeSections
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.SellerShop
import io.github.utshabasak.bookstorebd.core.model.Suggestions
import io.github.utshabasak.bookstorebd.core.model.map
import io.github.utshabasak.bookstorebd.core.network.BookStoreApi
import io.github.utshabasak.bookstorebd.core.network.Mappers
import io.github.utshabasak.bookstorebd.core.network.apiCall
import io.github.utshabasak.bookstorebd.core.network.dto.BookDto
import io.github.utshabasak.bookstorebd.core.network.dto.HomeSectionsDto
import io.github.utshabasak.bookstorebd.core.network.dto.SellerShopDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** The catalogue: home shelves, search, suggestions and seller shops. */
@Singleton
class CatalogueRepository
    @Inject
    constructor(
        private val api: BookStoreApi,
        private val cache: ResponseCache,
        private val mappers: Mappers,
        private val json: Json,
        private val recentSearchDao: RecentSearchDao,
        private val recentlyViewedDao: RecentlyViewedDao,
        private val settings: SettingsStore,
    ) {
        val homeSections: Flow<HomeSections?> =
            cache.observe(HOME, HomeSectionsDto.serializer()).map { it?.let(mappers::home) }

        suspend fun refreshHome(): Outcome<Unit> {
            val result = apiCall(json) { api.homeSections() }
            if (result is Outcome.Success) cache.put(HOME, HomeSectionsDto.serializer(), result.value)
            return result.map { }
        }

        /** The catalogue for [query], a page at a time, as the list scrolls. */
        fun search(query: CatalogueQuery): Flow<PagingData<Book>> =
            Pager(PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = PAGE_SIZE, enablePlaceholders = false)) {
                CataloguePagingSource(api, mappers, json, query)
            }.flow

        suspend fun suggest(text: String): Outcome<Suggestions> = apiCall(json) { api.suggest(text.trim()) }.map(mappers::suggestions)

        suspend fun popularSearches(): Outcome<List<String>> = apiCall(json) { api.popularSearches() }.map { it.terms }

        val recentSearches: Flow<List<String>> = recentSearchDao.observe(RECENT_SEARCHES)

        suspend fun rememberSearch(term: String) {
            val clean = term.trim()
            if (clean.isEmpty() || !settings.rememberSearches.first()) return
            recentSearchDao.put(RecentSearch(clean, System.currentTimeMillis()))
            recentSearchDao.trim(RECENT_SEARCHES)
        }

        suspend fun forgetSearch(term: String) = recentSearchDao.delete(term)

        suspend fun clearRecentSearches() = recentSearchDao.clear()

        fun shop(username: String): Flow<SellerShop?> =
            cache.observe(shopKey(username), SellerShopDto.serializer()).map {
                it?.let(mappers::shop)
            }

        suspend fun refreshShop(username: String): Outcome<Unit> {
            val result = apiCall(json) { api.shop(username) }
            if (result is Outcome.Success) cache.put(shopKey(username), SellerShopDto.serializer(), result.value)
            return result.map { }
        }

        /** Books opened lately, newest first: the home screen's Recently viewed. */
        val recentlyViewed: Flow<List<Book>> =
            cache.observe(RECENT_BOOKS, ListSerializer(BookDto.serializer())).map { it.orEmpty().map(mappers::book) }

        suspend fun recordView(bookId: String) {
            if (!settings.rememberViewed.first()) return
            recentlyViewedDao.put(RecentlyViewed(bookId, System.currentTimeMillis()))
            recentlyViewedDao.trim(RECENTLY_VIEWED)
        }

        suspend fun refreshRecentlyViewed(ids: List<String>): Outcome<Unit> {
            if (ids.isEmpty()) {
                cache.put(RECENT_BOOKS, ListSerializer(BookDto.serializer()), emptyList())
                return Outcome.Success(Unit)
            }
            val result = apiCall(json) { api.booksByIds(ids.joinToString(",")) }
            if (result is Outcome.Success) cache.put(RECENT_BOOKS, ListSerializer(BookDto.serializer()), result.value)
            return result.map { }
        }

        val recentlyViewedIds: Flow<List<String>> = recentlyViewedDao.observe(RECENTLY_VIEWED)

        suspend fun clearRecentlyViewed() {
            recentlyViewedDao.clear()
            cache.put(RECENT_BOOKS, ListSerializer(BookDto.serializer()), emptyList())
        }

        private fun shopKey(username: String) = "shop:${username.lowercase()}"

        private companion object {
            const val HOME = "home"
            const val RECENT_BOOKS = "recently-viewed"
            const val PAGE_SIZE = 20
            const val RECENT_SEARCHES = 8
            const val RECENTLY_VIEWED = 12
        }
    }

/** One search's pages, fetched as they are needed. */
internal class CataloguePagingSource(
    private val api: BookStoreApi,
    private val mappers: Mappers,
    private val json: Json,
    private val query: CatalogueQuery,
) : PagingSource<Int, Book>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Book> {
        val page = params.key ?: 1
        return when (val result = apiCall(json) { api.catalogue(filters(query, page, params.loadSize), query.categories.toList()) }) {
            is Outcome.Success -> {
                val body = result.value
                LoadResult.Page(
                    data = body.items.map(mappers::book),
                    prevKey = if (page > 1) page - 1 else null,
                    nextKey = if (page < body.pageCount) page + 1 else null,
                )
            }

            is Outcome.Failure -> {
                LoadResult.Error(CatalogueLoadException(result.error))
            }
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Book>): Int? = null

    companion object {
        /** The catalogue's named query parameters, as the API expects them. */
        fun filters(
            query: CatalogueQuery,
            page: Int,
            pageSize: Int,
        ): Map<String, String> =
            buildMap {
                query.search.trim().takeIf { it.isNotEmpty() }?.let { put("search", it) }
                query.type?.let { put("bookType", if (it == BookType.Old) "old" else "new") }
                query.condition?.let { put("condition", it) }
                query.minPrice?.let { put("minPrice", it.toString()) }
                query.maxPrice?.let { put("maxPrice", it.toString()) }
                query.minRating?.let { put("rating", it.toString()) }
                if (query.inStockOnly) put("inStock", "1")
                if (query.dealsOnly) put("deals", "1")
                query.seller?.let { put("seller", it) }
                put("sort", query.sort.apiValue)
                put("page", page.toString())
                put("pageSize", pageSize.coerceIn(1, MAX_PAGE_SIZE).toString())
            }

        private const val MAX_PAGE_SIZE = 50
    }
}

/** A page that could not be loaded, carrying the reason for the screen. */
class CatalogueLoadException(
    val error: AppError,
) : Exception("The catalogue could not be loaded: $error")
