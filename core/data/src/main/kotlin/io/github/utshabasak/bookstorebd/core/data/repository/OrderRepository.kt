package io.github.utshabasak.bookstorebd.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.utshabasak.bookstorebd.core.data.cache.ResponseCache
import io.github.utshabasak.bookstorebd.core.data.session.SessionManager
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Order
import io.github.utshabasak.bookstorebd.core.model.OrderQuery
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.ReturnDraft
import io.github.utshabasak.bookstorebd.core.model.map
import io.github.utshabasak.bookstorebd.core.network.AccountMappers
import io.github.utshabasak.bookstorebd.core.network.OrderApi
import io.github.utshabasak.bookstorebd.core.network.apiCall
import io.github.utshabasak.bookstorebd.core.network.dto.CancelOrderRequest
import io.github.utshabasak.bookstorebd.core.network.dto.OrderDetailDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.MultipartBody
import javax.inject.Inject
import javax.inject.Singleton

/** A page that could not load, carrying the reason for the screen to explain. */
class PageLoadException(
    val error: AppError,
) : Exception()

/**
 * The person's orders, their tracking pages, cancellations and returns. An
 * order's tracking page is kept under the account, so it opens at once,
 * offline too, and is refreshed whenever it is opened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class OrderRepository
    @Inject
    constructor(
        private val api: OrderApi,
        private val cache: ResponseCache,
        private val mappers: AccountMappers,
        private val json: Json,
        private val session: SessionManager,
        private val uploader: ImageUploader,
    ) {
        fun orders(query: OrderQuery): Flow<PagingData<Order>> =
            Pager(PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = PAGE_SIZE, enablePlaceholders = false)) {
                OrdersPagingSource(api, mappers, json, query)
            }.flow

        fun order(orderNumber: String): Flow<Order?> =
            session.user.flatMapLatest { user ->
                if (user == null) {
                    flowOf(null)
                } else {
                    cache.observe(ResponseCache.userKey(user.id, orderKey(orderNumber)), OrderDetailDto.serializer()).map {
                        it?.let(mappers::order)
                    }
                }
            }

        suspend fun refreshOrder(orderNumber: String): Outcome<Order> {
            val user = session.currentUser() ?: return Outcome.Failure(AppError.SignInRequired)
            return apiCall(json) { api.order(orderNumber) }
                .also {
                    if (it is Outcome.Success) {
                        cache.put(
                            ResponseCache.userKey(user.id, orderKey(orderNumber)),
                            OrderDetailDto.serializer(),
                            it.value,
                        )
                    }
                }.map(mappers::order)
        }

        /** Cancels the person's books in the order; the server decides whether it still can be. */
        suspend fun cancel(
            orderNumber: String,
            reason: String,
        ): Outcome<String> {
            val result =
                apiCall(json) {
                    api.cancel(orderNumber, CancelOrderRequest(reason.trim().takeIf { it.isNotEmpty() }))
                }.map { it.message }
            if (result is Outcome.Success) refreshOrder(orderNumber)
            return result
        }

        /**
         * Asks to return one or more books of a delivered order. The photos go
         * to the image host first when it is set up, as on the website, and in
         * the request itself when it is not.
         */
        suspend fun requestReturn(draft: ReturnDraft): Outcome<String> {
            val photos = uploader.upload(draft.photos)
            if (photos is Outcome.Failure) return photos
            val uploaded = (photos as Outcome.Success).value
            val parts =
                buildList {
                    draft.lineIds.forEach { add(MultipartBody.Part.createFormData("orderId", it)) }
                    add(MultipartBody.Part.createFormData("defectDescription", draft.description.trim()))
                    add(MultipartBody.Part.createFormData("refundBkash", draft.refundBkash.filter(Char::isDigit).takeLast(BKASH_DIGITS)))
                    addAll(uploaded.parts("images"))
                }
            return apiCall(json) { api.requestReturn(parts) }.map { it.message }
        }

        private fun orderKey(orderNumber: String) = "order:$orderNumber"

        private companion object {
            const val PAGE_SIZE = 20
            const val BKASH_DIGITS = 11
        }
    }

internal class OrdersPagingSource(
    private val api: OrderApi,
    private val mappers: AccountMappers,
    private val json: Json,
    private val query: OrderQuery,
) : PagingSource<Int, Order>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Order> {
        val page = params.key ?: 1
        return when (val result = apiCall(json) { api.buyerOrders(filters(query, page, params.loadSize)) }) {
            is Outcome.Success -> {
                LoadResult.Page(
                    data = mappers.orders(result.value.items),
                    prevKey = if (page > 1) page - 1 else null,
                    nextKey = if (page < result.value.pageCount) page + 1 else null,
                )
            }

            is Outcome.Failure -> {
                LoadResult.Error(PageLoadException(result.error))
            }
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Order>): Int? = null

    companion object {
        fun filters(
            query: OrderQuery,
            page: Int,
            pageSize: Int,
        ): Map<String, String> =
            buildMap {
                query.search.trim().takeIf { it.isNotEmpty() }?.let { put("search", it) }
                query.status?.let { put("status", it.apiValue) }
                put("sort", query.sort.apiValue)
                put("page", page.toString())
                put("pageSize", pageSize.toString())
            }
    }
}
