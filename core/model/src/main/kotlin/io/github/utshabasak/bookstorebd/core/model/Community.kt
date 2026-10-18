package io.github.utshabasak.bookstorebd.core.model

/** What is reviewed: a book, or a seller's shop. */
sealed interface ReviewTarget {
    val id: String

    data class OfBook(
        override val id: String,
    ) : ReviewTarget

    /** A seller, by account id. */
    data class OfSeller(
        override val id: String,
    ) : ReviewTarget
}

/**
 * Why somebody cannot review, when they cannot. A review needs a delivered book: [NotDelivered]
 * is an order still on its way, [NotPurchased] no order at all.
 */
enum class ReviewBlock { SignIn, OwnListing, OwnShop, NotPurchased, NotDelivered }

/** Reviews with what the signed-in person can do with them. */
data class ReviewPage(
    val summary: ReviewSummary,
    val mine: Review?,
    val canReview: Boolean,
    val block: ReviewBlock?,
    /** True for the book's seller, who may reply to reviews. */
    val isSeller: Boolean,
)

data class ReviewDraft(
    val rating: Int,
    val title: String,
    val body: String,
)

enum class WantedStatus { Open, Found }

enum class WantedSort(
    val apiValue: String,
) {
    Popular("popular"),
    Newest("newest"),
}

/** A book somebody is looking for, on the Wanted board. */
data class WantedItem(
    val id: String,
    val title: String,
    val author: String,
    val isbn: String,
    val details: String,
    /** How many people want it. */
    val count: Int,
    val wantedByMe: Boolean,
    val status: WantedStatus,
    val foundBookId: String?,
    val foundBookTitle: String?,
    val createdAt: String?,
)

data class WantedQuery(
    val search: String = "",
    val status: WantedStatus = WantedStatus.Open,
    val sort: WantedSort = WantedSort.Popular,
    val mineOnly: Boolean = false,
)

data class WantedDraft(
    val title: String,
    val author: String = "",
    val isbn: String = "",
    val details: String = "",
)

/** What asking for a book did. */
sealed interface WantedResult {
    val message: String

    data class Created(
        override val message: String,
    ) : WantedResult

    /** Somebody had already asked; this person joined them. */
    data class Joined(
        override val message: String,
    ) : WantedResult

    /** The book is in the shop already. */
    data class Listed(
        override val message: String,
        val bookId: String?,
    ) : WantedResult
}
