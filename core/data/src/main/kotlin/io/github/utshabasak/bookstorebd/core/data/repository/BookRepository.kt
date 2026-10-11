package io.github.utshabasak.bookstorebd.core.data.repository

import io.github.utshabasak.bookstorebd.core.data.cache.ResponseCache
import io.github.utshabasak.bookstorebd.core.model.BookDetail
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.ReviewSummary
import io.github.utshabasak.bookstorebd.core.model.SellerSummary
import io.github.utshabasak.bookstorebd.core.model.map
import io.github.utshabasak.bookstorebd.core.network.BookStoreApi
import io.github.utshabasak.bookstorebd.core.network.ImageUrls
import io.github.utshabasak.bookstorebd.core.network.Mappers
import io.github.utshabasak.bookstorebd.core.network.apiCall
import io.github.utshabasak.bookstorebd.core.network.dto.BookDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** One book's page: the book, its reviews, and asking for it to come back. */
@Singleton
class BookRepository
    @Inject
    constructor(
        private val api: BookStoreApi,
        private val cache: ResponseCache,
        private val mappers: Mappers,
        private val json: Json,
        private val images: ImageUrls,
    ) {
        fun book(id: String): Flow<BookDetail?> = cache.observe(key(id), BookDto.serializer()).map { it?.let(mappers::bookDetail) }

        suspend fun refreshBook(id: String): Outcome<Unit> {
            val result = apiCall(json) { api.book(id) }
            if (result is Outcome.Success) cache.put(key(id), BookDto.serializer(), result.value)
            return result.map { }
        }

        suspend fun reviews(id: String): Outcome<ReviewSummary> = apiCall(json) { api.reviews(id) }.map(mappers::reviews)

        /** Whether the signed-in person has asked to hear when this sold-out book is back. */
        suspend fun isRequested(id: String): Outcome<Boolean> = apiCall(json) { api.bookRequest(id) }.map { it.requested }

        suspend fun setRequested(
            id: String,
            requested: Boolean,
        ): Outcome<Boolean> =
            apiCall(json) { if (requested) api.requestBook(id) else api.withdrawBookRequest(id) }.map { it.requested }

        /** Who sells it: the listing names an e-mail address, the shop is found by username. */
        suspend fun seller(email: String): Outcome<SellerSummary> =
            apiCall(json) { api.profile(email) }.map {
                SellerSummary(it.username, images.resolve(it.profilePicture), it.sellerRating?.average ?: 0.0, it.sellerRating?.count ?: 0)
            }

        private fun key(id: String) = "book:$id"
    }
