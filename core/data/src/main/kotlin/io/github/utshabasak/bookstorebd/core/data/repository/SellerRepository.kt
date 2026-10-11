package io.github.utshabasak.bookstorebd.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.utshabasak.bookstorebd.core.data.session.SessionManager
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.Discount
import io.github.utshabasak.bookstorebd.core.model.DiscountKind
import io.github.utshabasak.bookstorebd.core.model.ImageUpload
import io.github.utshabasak.bookstorebd.core.model.Listing
import io.github.utshabasak.bookstorebd.core.model.ListingDraft
import io.github.utshabasak.bookstorebd.core.model.Order
import io.github.utshabasak.bookstorebd.core.model.OrderStatus
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.Published
import io.github.utshabasak.bookstorebd.core.model.Sale
import io.github.utshabasak.bookstorebd.core.model.SalesQuery
import io.github.utshabasak.bookstorebd.core.model.map
import io.github.utshabasak.bookstorebd.core.network.AccountMappers
import io.github.utshabasak.bookstorebd.core.network.OrderApi
import io.github.utshabasak.bookstorebd.core.network.SellerApi
import io.github.utshabasak.bookstorebd.core.network.SellerMappers
import io.github.utshabasak.bookstorebd.core.network.apiCall
import io.github.utshabasak.bookstorebd.core.network.dto.CancelOrderRequest
import io.github.utshabasak.bookstorebd.core.network.dto.DiscountRequest
import io.github.utshabasak.bookstorebd.core.network.dto.PriceRequest
import io.github.utshabasak.bookstorebd.core.network.dto.StatusRequest
import io.github.utshabasak.bookstorebd.core.network.dto.StockRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import okhttp3.MultipartBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selling, as on the website: listing a book, keeping its price, stock and
 * discount right, and moving the orders for it on.
 */
@Singleton
class SellerRepository
    @Inject
    constructor(
        private val api: SellerApi,
        private val orders: OrderApi,
        private val mappers: SellerMappers,
        private val orderMappers: AccountMappers,
        private val uploader: ImageUploader,
        private val session: SessionManager,
        private val json: Json,
    ) {
        /** The seller's listings, newest first as the server sends them, with buyers waiting on sold-out ones. */
        suspend fun listings(): Outcome<List<Listing>> {
            val me = session.currentUser()?.email ?: return Outcome.Failure(AppError.SignInRequired)
            return coroutineScope {
                val waiting = async { apiCall(json) { api.requestCounts() } }
                val books = apiCall(json) { api.listings(me) }
                val counts = (waiting.await() as? Outcome.Success)?.value.orEmpty()
                books.map { list -> list.map { mappers.listing(it, counts[it.id] ?: 0) } }
            }
        }

        suspend fun setPrice(
            id: String,
            price: Int,
        ): Outcome<String> = apiCall(json) { api.setPrice(id, PriceRequest(price)) }.map { it.message }

        suspend fun setStock(
            id: String,
            stock: Int,
        ): Outcome<String> = apiCall(json) { api.setStock(id, StockRequest(stock)) }.map { it.message }

        suspend fun setDiscount(
            id: String,
            discount: Discount,
        ): Outcome<String> =
            apiCall(json) {
                api.setDiscount(
                    id,
                    if (discount.kind ==
                        DiscountKind.None
                    ) {
                        DiscountRequest("none")
                    } else {
                        DiscountRequest(discount.kind.apiValue, discount.value)
                    },
                )
            }.map { it.message }

        suspend fun delete(id: String): Outcome<String> = apiCall(json) { api.delete(id) }.map { it.message }

        /**
         * Publishes a listing: the photos first, straight to the image host (or
         * with the form when the shop hosts them itself), then the form. Blank
         * optional details are sent as "N/A", as the website does, because the
         * server requires them.
         */
        suspend fun publish(
            draft: ListingDraft,
            photos: List<ImageUpload>,
        ): Outcome<Published> {
            val uploaded =
                when (val result = uploader.upload(photos)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> result.value
                }
            val parts = fields(draft) + uploaded.parts("images")
            return apiCall(json) { api.addBook(parts) }.map { Published(it.message, it.book?.id, it.waiting) }
        }

        fun sales(query: SalesQuery): Flow<PagingData<Sale>> =
            Pager(PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = PAGE_SIZE, enablePlaceholders = false)) {
                SalesPagingSource(api, mappers, json, query)
            }.flow

        /** An order as its seller sees it: only their own books, with what they may do next. */
        suspend fun order(orderNumber: String): Outcome<Order> {
            val me = session.currentUser()?.email ?: return Outcome.Failure(AppError.SignInRequired)
            return apiCall(json) { orders.order(orderNumber) }.map { dto ->
                val order = orderMappers.order(dto)
                order.copy(lines = order.lines.filter { it.sellerEmail.equals(me, ignoreCase = true) })
            }
        }

        suspend fun setStatus(
            orderNumber: String,
            status: OrderStatus,
        ): Outcome<Unit> = apiCall(json) { api.setStatus(orderNumber, StatusRequest(status.apiValue)) }.map { }

        suspend fun cancel(
            orderNumber: String,
            reason: String,
        ): Outcome<String> =
            apiCall(json) { orders.cancel(orderNumber, CancelOrderRequest(reason.trim().takeIf { it.isNotEmpty() })) }
                .map { it.message }

        private fun fields(draft: ListingDraft): List<MultipartBody.Part> =
            buildList {
                fun field(
                    name: String,
                    value: String,
                ) = add(MultipartBody.Part.createFormData(name, value))

                field("title", draft.title.trim())
                field("author", draft.author.trim())
                field("publisher", draft.publisher.orNotGiven())
                field("country", draft.country.orNotGiven())
                field("language", draft.language.orNotGiven())
                field("isbn", draft.isbn.orNotGiven())
                draft.pages.trim().takeIf { it.isNotEmpty() }?.let { field("pages", it) }
                field("price", draft.price.trim())
                field("desc", draft.description.orNotGiven())
                draft.categories.forEach { field("category", it) }
                field("bookType", if (draft.type == BookType.Old) "old" else "new")
                if (draft.type == BookType.Old) {
                    field("condition", draft.condition)
                    draft.conditionDetails.trim().takeIf { it.isNotEmpty() }?.let { field("conditionDetails", it) }
                }
                if (draft.discount.kind != DiscountKind.None) {
                    field("discountType", draft.discount.kind.apiValue)
                    field("discountValue", draft.discount.value.toString())
                }
            }

        private fun String.orNotGiven(): String = trim().ifEmpty { NOT_GIVEN }

        private companion object {
            const val PAGE_SIZE = 25
            const val NOT_GIVEN = "N/A"
        }
    }

internal class SalesPagingSource(
    private val api: SellerApi,
    private val mappers: SellerMappers,
    private val json: Json,
    private val query: SalesQuery,
) : PagingSource<Int, Sale>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Sale> {
        val page = params.key ?: 1
        val filters =
            buildMap {
                query.search.trim().takeIf { it.isNotEmpty() }?.let { put("search", it) }
                query.status?.let { put("status", it.apiValue) }
                put("sort", query.sort.apiValue)
                put("page", page.toString())
                put("pageSize", params.loadSize.coerceAtMost(MAX_PAGE).toString())
            }
        return when (val result = apiCall(json) { api.sales(filters) }) {
            is Outcome.Success -> {
                LoadResult.Page(
                    data = result.value.items.map(mappers::sale),
                    prevKey = if (page > 1) page - 1 else null,
                    nextKey = if (page < result.value.pageCount) page + 1 else null,
                )
            }

            is Outcome.Failure -> {
                LoadResult.Error(PageLoadException(result.error))
            }
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Sale>): Int? = null

    private companion object {
        const val MAX_PAGE = 50
    }
}
