package io.github.utshabasak.bookstorebd.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SessionUserDto(
    val id: String,
    val username: String = "",
    val email: String = "",
    val role: String = "user",
)

/** The phone a session starts on (AppDevice in api.d.ts). */
@Serializable
data class AppDeviceDto(
    val publicKey: String,
    val name: String,
    val installId: String,
)

@Serializable
data class AppSignInRequest(
    val email: String,
    val password: String,
    val captchaToken: String? = null,
    val device: AppDeviceDto,
)

@Serializable
data class AppVerifySignInRequest(
    val email: String,
    val code: String,
    val device: AppDeviceDto,
)

@Serializable
data class AppSignUpRequest(
    val username: String,
    val email: String,
    val password: String,
    val device: AppDeviceDto,
)

@Serializable
data class AppGoogleSignInRequest(
    val idToken: String,
    val nonce: String,
    val device: AppDeviceDto,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

/**
 * Sign-in's answer: a session, or - with two-step sign-in on - a challenge
 * saying the code is on its way. One type with optional fields, since the
 * server sends one or the other.
 */
@Serializable
data class AppSignInResponse(
    val token: String? = null,
    val user: SessionUserDto? = null,
    val refreshToken: String? = null,
    val refreshExpiresAt: String? = null,
    val twoFactor: Boolean = false,
    val sentTo: String? = null,
    val email: String? = null,
    val message: String? = null,
)

@Serializable
data class NonceDto(
    val nonce: String,
)

@Serializable
data class SendOtpRequest(
    val email: String,
    val username: String? = null,
    val purpose: String,
    val captchaToken: String? = null,
)

@Serializable
data class VerifyOtpRequest(
    val email: String,
    val code: String,
)

@Serializable
data class ResetPasswordRequest(
    val email: String,
    val otp: String,
    val newPassword: String,
)

@Serializable
data class PasswordCheckRequest(
    val password: String,
    val email: String? = null,
    val username: String? = null,
)

@Serializable
data class PasswordCheckDto(
    val ok: Boolean = false,
    val problems: List<String> = emptyList(),
    val message: String = "",
)

@Serializable
data class MessageDto(
    val message: String = "",
)

@Serializable
data class SignOutOthersDto(
    val message: String = "",
    val ended: Int = 0,
)

/**
 * GET /user/profile: a person's public profile, plus their contact details
 * when it is their own (which checkout pre-fills).
 */
@Serializable
data class ProfileDto(
    val username: String = "",
    val email: String = "",
    val profilePicture: String? = null,
    val sellerRating: RatingDto? = null,
    val phone: String? = null,
    val address: String? = null,
)
