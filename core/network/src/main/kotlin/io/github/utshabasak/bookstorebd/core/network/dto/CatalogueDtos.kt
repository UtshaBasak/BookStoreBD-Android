package io.github.utshabasak.bookstorebd.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Wire types, mirroring server/shared/api.d.ts. Every field the server may
 * leave out has a default, and unknown fields are ignored, so the API can add
 * to a response without breaking an installed app.
 */

@Serializable
data class BookDto(
    @SerialName("_id") val id: String,
    val title: String = "",
    val author: String = "",
    val publisher: String = "",
    val language: String = "",
    val isbn: String = "",
    val pages: Int? = null,
    val price: Double = 0.0,
    val desc: String = "",
    val category: List<String> = emptyList(),
    val bookType: String = "new",
    val condition: String? = null,
    val conditionDetails: String? = null,
    val images: List<String> = emptyList(),
    val sellerEmail: String = "",
    val stock: Int = 0,
    val ratingAverage: Double = 0.0,
    val ratingCount: Int = 0,
    val viewCount: Int = 0,
    val wishlistCount: Int = 0,
    val salePrice: Double? = null,
    val discountPercent: Double = 0.0,
    val cartQuantity: Int? = null,
    val cartAdjusted: Boolean = false,
    /** Only on GET /book/:id. */
    val relatedBooks: List<BookDto> = emptyList(),
)

@Serializable
data class CataloguePageDto(
    val items: List<BookDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
    val pageCount: Int = 0,
)

@Serializable
data class HomeSectionsDto(
    val deals: List<BookDto> = emptyList(),
    val trending: List<BookDto> = emptyList(),
    val popular: List<BookDto> = emptyList(),
    val bestsellers: List<BookDto> = emptyList(),
    val topRated: List<BookDto> = emptyList(),
    val budget: List<BookDto> = emptyList(),
    val discover: List<BookDto> = emptyList(),
    val writers: List<PopularWriterDto> = emptyList(),
    val categories: List<CountDto> = emptyList(),
)

@Serializable
data class PopularWriterDto(
    val name: String,
    val books: Int = 0,
    val sold: Int = 0,
)

@Serializable
data class CountDto(
    val name: String,
    val books: Int = 0,
)

@Serializable
data class SuggestResponseDto(
    val books: List<SuggestBookDto> = emptyList(),
    val sellers: List<SellerHitDto> = emptyList(),
    val authors: List<CountDto> = emptyList(),
    val categories: List<CountDto> = emptyList(),
)

@Serializable
data class SuggestBookDto(
    @SerialName("_id") val id: String,
    val title: String = "",
    val author: String = "",
    val cover: String? = null,
    val price: Double = 0.0,
    val salePrice: Double? = null,
    val discountPercent: Double = 0.0,
    val inStock: Boolean = true,
)

@Serializable
data class SellerHitDto(
    val username: String,
    val avatar: String? = null,
    val books: Int = 0,
)

@Serializable
data class PopularSearchesDto(
    val terms: List<String> = emptyList(),
)

@Serializable
data class ForYouDto(
    val items: List<BookDto> = emptyList(),
    val personal: Boolean = false,
)

@Serializable
data class SellerShopDto(
    val id: String,
    val username: String,
    val email: String = "",
    val profilePicture: String? = null,
    val sellerBanner: String? = null,
    val joinedAt: String? = null,
    val books: Int = 0,
    val inStock: Int = 0,
    val sold: Int = 0,
    val ratingAverage: Double = 0.0,
    val ratingCount: Int = 0,
    val sellerRating: RatingDto = RatingDto(),
)

@Serializable
data class RatingDto(
    val average: Double = 0.0,
    val count: Int = 0,
)

@Serializable
data class ReviewSummaryDto(
    val average: Double = 0.0,
    val count: Int = 0,
    val distribution: List<Int> = emptyList(),
    val reviews: List<ReviewDto> = emptyList(),
)

@Serializable
data class ReviewDto(
    @SerialName("_id") val id: String,
    val reviewerName: String = "",
    val rating: Int = 0,
    val title: String? = null,
    val body: String? = null,
    val createdAt: String? = null,
    val reply: ReviewReplyDto? = null,
)

@Serializable
data class ReviewReplyDto(
    val body: String = "",
    val byName: String = "",
    val at: String? = null,
)

@Serializable
data class BookRequestStatusDto(
    val requested: Boolean = false,
    val count: Int = 0,
)
