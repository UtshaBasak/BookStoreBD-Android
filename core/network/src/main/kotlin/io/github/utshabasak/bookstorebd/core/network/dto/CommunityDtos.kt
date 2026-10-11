package io.github.utshabasak.bookstorebd.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** ReviewSummary and SellerReviewSummary, with what the caller may do. */
@Serializable
data class ReviewPageDto(
    val average: Double = 0.0,
    val count: Int = 0,
    val distribution: List<Int> = emptyList(),
    val reviews: List<ReviewDto> = emptyList(),
    val mine: ReviewDto? = null,
    val canReview: Boolean = false,
    val reason: String? = null,
    val isSeller: Boolean = false,
)

@Serializable
data class WriteReviewRequest(
    val rating: Int,
    val title: String? = null,
    val body: String? = null,
)

@Serializable
data class ReplyRequest(
    val body: String,
)

@Serializable
data class FlagRequest(
    val reason: String? = null,
)

@Serializable
data class FoundBookDto(
    @SerialName("_id") val id: String,
    val title: String = "",
)

@Serializable
data class WantedItemDto(
    @SerialName("_id") val id: String,
    val title: String = "",
    val author: String? = null,
    val isbn: String? = null,
    val details: String? = null,
    val count: Int = 0,
    val wantedByMe: Boolean = false,
    val status: String = "open",
    val foundBook: FoundBookDto? = null,
    val createdAt: String? = null,
    val foundAt: String? = null,
)

@Serializable
data class CreateWantedRequest(
    val title: String,
    val author: String? = null,
    val isbn: String? = null,
    val details: String? = null,
)

@Serializable
data class CreateWantedDto(
    val result: String = "created",
    val message: String = "",
    val item: WantedItemDto? = null,
    val book: FoundBookDto? = null,
)

@Serializable
data class LeaveWantedDto(
    val message: String = "",
    val item: WantedItemDto? = null,
)
