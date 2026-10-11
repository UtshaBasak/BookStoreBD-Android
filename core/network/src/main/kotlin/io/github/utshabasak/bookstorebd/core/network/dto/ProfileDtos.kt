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
)

@Serializable
data class TwoFactorRequest(
    val enabled: Boolean,
    val password: String? = null,
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

@Serializable
data class PasswordRequest(
    val password: String,
)
