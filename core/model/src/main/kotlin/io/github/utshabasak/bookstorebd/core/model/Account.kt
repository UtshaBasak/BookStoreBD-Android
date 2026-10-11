package io.github.utshabasak.bookstorebd.core.model

/** Who is signed in, as the session describes them. */
data class SessionUser(
    val id: String,
    val username: String,
    val email: String,
    val isAdmin: Boolean,
)

/** The result of a sign-in attempt that did not fail outright. */
sealed interface SignInOutcome {
    data class SignedIn(
        val user: SessionUser,
    ) : SignInOutcome

    /** Two-step sign-in: a code went to [sentTo], for [email]. */
    data class CodeSent(
        val email: String,
        val sentTo: String,
    ) : SignInOutcome
}

/** Whether a password would be accepted, and if not, why. */
data class PasswordCheck(
    val ok: Boolean,
    val problems: List<String>,
    val message: String,
)

/** One book's reviews, as its page shows them. */
data class ReviewSummary(
    val average: Double,
    val count: Int,
    /** How many gave one star, two, and so on; index 0 is one star. */
    val distribution: List<Int>,
    val reviews: List<Review>,
)

data class Review(
    val id: String,
    val reviewerName: String,
    val rating: Int,
    val title: String,
    val body: String,
    val createdAt: String?,
    val reply: ReviewReply?,
    val reviewerEmail: String = "",
)

data class ReviewReply(
    val body: String,
    val byName: String,
    val at: String?,
)

/** Contact details checkout pre-fills from the profile. */
data class ContactDetails(
    val name: String = "",
    val phone: String = "",
    val address: String = "",
)
