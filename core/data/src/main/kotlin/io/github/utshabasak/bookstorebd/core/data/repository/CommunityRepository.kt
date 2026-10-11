package io.github.utshabasak.bookstorebd.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.ReviewDraft
import io.github.utshabasak.bookstorebd.core.model.ReviewPage
import io.github.utshabasak.bookstorebd.core.model.ReviewTarget
import io.github.utshabasak.bookstorebd.core.model.WantedDraft
import io.github.utshabasak.bookstorebd.core.model.WantedItem
import io.github.utshabasak.bookstorebd.core.model.WantedQuery
import io.github.utshabasak.bookstorebd.core.model.WantedResult
import io.github.utshabasak.bookstorebd.core.model.WantedStatus
import io.github.utshabasak.bookstorebd.core.model.map
import io.github.utshabasak.bookstorebd.core.network.AccountMappers
import io.github.utshabasak.bookstorebd.core.network.CommunityApi
import io.github.utshabasak.bookstorebd.core.network.apiCall
import io.github.utshabasak.bookstorebd.core.network.dto.CreateWantedRequest
import io.github.utshabasak.bookstorebd.core.network.dto.FlagRequest
import io.github.utshabasak.bookstorebd.core.network.dto.ReplyRequest
import io.github.utshabasak.bookstorebd.core.network.dto.WriteReviewRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Reviews of books, ratings of sellers, and the Wanted board. */
@Singleton
class CommunityRepository
    @Inject
    constructor(
        private val api: CommunityApi,
        private val mappers: AccountMappers,
        private val json: Json,
    ) {
        private val ReviewTarget.kind get() = if (this is ReviewTarget.OfSeller) "seller-review" else "review"

        suspend fun reviews(target: ReviewTarget): Outcome<ReviewPage> =
            apiCall(json) {
                api.reviews(target.kind, target.id)
            }.map(mappers::reviewPage)

        /** Writes or replaces the person's own review. */
        suspend fun writeReview(
            target: ReviewTarget,
            draft: ReviewDraft,
        ): Outcome<Unit> =
            apiCall(json) {
                api.writeReview(
                    target.kind,
                    target.id,
                    WriteReviewRequest(
                        draft.rating,
                        draft.title.trim().takeIf {
                            it.isNotEmpty()
                        },
                        draft.body.trim().takeIf { it.isNotEmpty() },
                    ),
                )
            }.map { }

        suspend fun deleteReview(target: ReviewTarget): Outcome<Unit> = apiCall(json) { api.deleteReview(target.kind, target.id) }.map { }

        suspend fun reply(
            target: ReviewTarget,
            reviewId: String,
            body: String,
        ): Outcome<Unit> = apiCall(json) { api.reply(target.kind, reviewId, ReplyRequest(body.trim())) }.map { }

        suspend fun deleteReply(
            target: ReviewTarget,
            reviewId: String,
        ): Outcome<Unit> = apiCall(json) { api.deleteReply(target.kind, reviewId) }.map { }

        suspend fun report(
            target: ReviewTarget,
            reviewId: String,
            reason: String,
        ): Outcome<String> =
            apiCall(json) {
                api.report(target.kind, reviewId, FlagRequest(reason.trim().takeIf { it.isNotEmpty() }))
            }.map { it.message }

        fun wanted(query: WantedQuery): Flow<PagingData<WantedItem>> =
            Pager(PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = PAGE_SIZE, enablePlaceholders = false)) {
                WantedPagingSource(api, mappers, json, query)
            }.flow

        suspend fun askForBook(draft: WantedDraft): Outcome<WantedResult> =
            apiCall(json) {
                api.askForBook(
                    CreateWantedRequest(
                        title = draft.title.trim(),
                        author = draft.author.trim().takeIf { it.isNotEmpty() },
                        isbn = draft.isbn.trim().takeIf { it.isNotEmpty() },
                        details = draft.details.trim().takeIf { it.isNotEmpty() },
                    ),
                )
            }.map {
                when (it.result) {
                    "joined" -> WantedResult.Joined(it.message)
                    "listed" -> WantedResult.Listed(it.message, it.book?.id)
                    else -> WantedResult.Created(it.message)
                }
            }

        suspend fun join(id: String): Outcome<WantedItem> = apiCall(json) { api.joinWanted(id) }.map(mappers::wanted)

        suspend fun leave(id: String): Outcome<String> = apiCall(json) { api.leaveWanted(id) }.map { it.message }

        private companion object {
            const val PAGE_SIZE = 20
        }
    }

internal class WantedPagingSource(
    private val api: CommunityApi,
    private val mappers: AccountMappers,
    private val json: Json,
    private val query: WantedQuery,
) : PagingSource<Int, WantedItem>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, WantedItem> {
        val page = params.key ?: 1
        val filters =
            buildMap {
                query.search.trim().takeIf { it.isNotEmpty() }?.let { put("search", it) }
                put("status", if (query.status == WantedStatus.Found) "found" else "open")
                put("sort", query.sort.apiValue)
                if (query.mineOnly) put("mine", "1")
                put("page", page.toString())
                put("pageSize", params.loadSize.coerceAtMost(MAX_PAGE).toString())
            }
        return when (val result = apiCall(json) { api.wanted(filters) }) {
            is Outcome.Success -> {
                LoadResult.Page(
                    data = result.value.items.map(mappers::wanted),
                    prevKey = if (page > 1) page - 1 else null,
                    nextKey = if (page < result.value.pageCount) page + 1 else null,
                )
            }

            is Outcome.Failure -> {
                LoadResult.Error(PageLoadException(result.error))
            }
        }
    }

    override fun getRefreshKey(state: PagingState<Int, WantedItem>): Int? = null

    private companion object {
        const val MAX_PAGE = 50
    }
}
