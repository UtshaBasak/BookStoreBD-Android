package io.github.utshabasak.bookstorebd.core.network.dto

import kotlinx.serialization.Serializable

/** OwnProfile: GET /user/profile for the signed-in person. */
@Serializable
data class OwnProfileDto(
    val username: String = "",
    val email: String = "",
    val profilePicture: String? = null,
    val buyerBanner: String? = null,
    val sellerBanner: String? = null,
    val sellerRating: RatingDto = RatingDto(),
    val address: String? = null,
    val phone: String? = null,
    val bkashMerchant: String? = null,
    val dateOfBirth: String? = null,
    val gender: String? = null,
    val role: String = "user",
    val twoFactor: Boolean = false,
    val passwordSet: Boolean = true,
    /** An address the account is waiting to move to, once its code is entered. */
    val pendingEmail: String? = null,
)

/** Turning it off asks for the password, or for an account without one, an e-mailed code. */
@Serializable
data class TwoFactorRequest(
    val enabled: Boolean,
    val password: String? = null,
    val code: String? = null,
)

/** DELETE /user/me: the password, or for an account without one, the e-mailed code. */
@Serializable
data class DeleteAccountRequest(
    val password: String? = null,
    val code: String? = null,
)

/** POST /user/me/confirm-code: e-mails a code, for an account with no password. */
@Serializable
data class ConfirmCodeRequest(
    /** delete-account or two-factor-off. */
    val action: String,
)

@Serializable
data class EmailChangeRequest(
    val newEmail: String,
    val password: String,
)

@Serializable
data class EmailChangeDto(
    val message: String = "",
    val pendingEmail: String = "",
)

@Serializable
data class EmailConfirmRequest(
    val code: String,
)

@Serializable
data class EmailConfirmDto(
    val message: String = "",
    val email: String,
)

@Serializable
data class TwoFactorDto(
    val message: String = "",
    val twoFactor: Boolean = false,
)

@Serializable
data class InviteeDto(
    val email: String,
    val invitedAt: String? = null,
    val joined: Boolean = false,
)

@Serializable
data class InviteListDto(
    val items: List<InviteeDto> = emptyList(),
    val remainingToday: Int = 0,
)

@Serializable
data class InviteRequest(
    val emails: List<String>,
    val note: String? = null,
)

@Serializable
data class InviteSentDto(
    val message: String = "",
    val sent: Int = 0,
)
