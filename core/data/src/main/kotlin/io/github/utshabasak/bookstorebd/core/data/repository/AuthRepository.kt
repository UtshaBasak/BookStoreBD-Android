package io.github.utshabasak.bookstorebd.core.data.repository

import io.github.utshabasak.bookstorebd.core.common.BookStoreDispatchers
import io.github.utshabasak.bookstorebd.core.common.Dispatcher
import io.github.utshabasak.bookstorebd.core.data.session.SessionManager
import io.github.utshabasak.bookstorebd.core.datastore.DeviceKeys
import io.github.utshabasak.bookstorebd.core.datastore.SettingsStore
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.PasswordCheck
import io.github.utshabasak.bookstorebd.core.model.SignInOutcome
import io.github.utshabasak.bookstorebd.core.model.map
import io.github.utshabasak.bookstorebd.core.network.BookStoreApi
import io.github.utshabasak.bookstorebd.core.network.Mappers
import io.github.utshabasak.bookstorebd.core.network.NetworkConfig
import io.github.utshabasak.bookstorebd.core.network.SessionApi
import io.github.utshabasak.bookstorebd.core.network.apiCall
import io.github.utshabasak.bookstorebd.core.network.dto.AppDeviceDto
import io.github.utshabasak.bookstorebd.core.network.dto.AppGoogleSignInRequest
import io.github.utshabasak.bookstorebd.core.network.dto.AppSignInRequest
import io.github.utshabasak.bookstorebd.core.network.dto.AppSignInResponse
import io.github.utshabasak.bookstorebd.core.network.dto.AppSignUpRequest
import io.github.utshabasak.bookstorebd.core.network.dto.AppVerifySignInRequest
import io.github.utshabasak.bookstorebd.core.network.dto.PasswordCheckRequest
import io.github.utshabasak.bookstorebd.core.network.dto.RefreshRequest
import io.github.utshabasak.bookstorebd.core.network.dto.ResetPasswordRequest
import io.github.utshabasak.bookstorebd.core.network.dto.SendOtpRequest
import io.github.utshabasak.bookstorebd.core.network.dto.VerifyOtpRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signing in, up and out, and the codes and passwords around them. Every way
 * in starts the session on this phone: a fresh keystore key is made, and its
 * public half goes to the server with the request.
 */
@Singleton
class AuthRepository
    @Inject
    constructor(
        private val api: BookStoreApi,
        private val sessionApi: SessionApi,
        private val session: SessionManager,
        private val deviceKeys: DeviceKeys,
        private val settings: SettingsStore,
        private val mappers: Mappers,
        private val config: NetworkConfig,
        private val json: Json,
        @Dispatcher(BookStoreDispatchers.Default) private val default: CoroutineDispatcher,
    ) {
        suspend fun signIn(
            email: String,
            password: String,
            captchaToken: String?,
        ): Outcome<SignInOutcome> {
            val device = device(newKey = true)
            return outcome(email) { sessionApi.signIn(AppSignInRequest(email.trim(), password, captchaToken, device)) }
        }

        /** Finishes a two-step sign-in with the e-mailed code, on the key made at the first step. */
        suspend fun verifySignIn(
            email: String,
            code: String,
        ): Outcome<SignInOutcome> {
            val device = device(newKey = false)
            return outcome(email) { sessionApi.verifySignIn(AppVerifySignInRequest(email.trim(), code.trim(), device)) }
        }

        suspend fun googleNonce(): Outcome<String> = apiCall(json) { sessionApi.nonce() }.map { it.nonce }

        suspend fun signInWithGoogle(
            idToken: String,
            nonce: String,
        ): Outcome<SignInOutcome> {
            val device = device(newKey = true)
            return outcome(email = "") { sessionApi.googleSignIn(AppGoogleSignInRequest(idToken, nonce, device)) }
        }

        /** Sends the sign-up code. The answer is the same whether or not the address is free. */
        suspend fun sendSignUpCode(
            email: String,
            username: String,
            captchaToken: String?,
        ): Outcome<String> =
            apiCall(json) { api.sendCode(SendOtpRequest(email.trim(), username.trim(), "register", captchaToken)) }.map { it.message }

        suspend fun verifyCode(
            email: String,
            code: String,
        ): Outcome<Unit> = apiCall(json) { api.verifyCode(VerifyOtpRequest(email.trim(), code.trim())) }.map { }

        suspend fun signUp(
            username: String,
            email: String,
            password: String,
        ): Outcome<SignInOutcome> {
            val device = device(newKey = true)
            return outcome(email) { sessionApi.signUp(AppSignUpRequest(username.trim(), email.trim(), password, device)) }
        }

        suspend fun sendResetCode(
            email: String,
            captchaToken: String?,
        ): Outcome<String> =
            apiCall(json) {
                api.sendCode(SendOtpRequest(email.trim(), purpose = "reset", captchaToken = captchaToken))
            }.map { it.message }

        suspend fun resetPassword(
            email: String,
            code: String,
            newPassword: String,
        ): Outcome<String> =
            apiCall(json) {
                api.resetPassword(ResetPasswordRequest(email.trim(), code.trim(), newPassword))
            }.map { it.message }

        suspend fun checkPassword(
            password: String,
            email: String,
            username: String,
        ): Outcome<PasswordCheck> =
            apiCall(json) { api.passwordCheck(PasswordCheckRequest(password, email.trim(), username.trim())) }.map(mappers::passwordCheck)

        suspend fun signOut() = session.signOut()

        /** Signs out every other phone and browser, keeping this one. Returns how many sessions ended. */
        suspend fun signOutOthers(): Outcome<Int> {
            val token = session.refreshToken() ?: return Outcome.Failure(AppError.SignInRequired)
            return apiCall(json) { api.signOutOthers(RefreshRequest(token)) }.map { it.ended }
        }

        private suspend fun outcome(
            email: String,
            call: suspend () -> AppSignInResponse,
        ): Outcome<SignInOutcome> =
            when (val result = apiCall(json, credentials = true) { call() }) {
                is Outcome.Failure -> {
                    result
                }

                is Outcome.Success -> {
                    val body = result.value
                    if (body.twoFactor) {
                        Outcome.Success(SignInOutcome.CodeSent(email = body.email ?: email.trim(), sentTo = body.sentTo.orEmpty()))
                    } else {
                        session.start(body)?.let { Outcome.Success(SignInOutcome.SignedIn(it)) }
                            ?: Outcome.Failure(AppError.Rejected(status = 200, message = null))
                    }
                }
            }

        /** The phone, as a sign-in request describes it. Making a keystore key takes a moment, so not on the main thread. */
        private suspend fun device(newKey: Boolean): AppDeviceDto =
            withContext(default) {
                val publicKey = if (newKey) deviceKeys.createKey() else deviceKeys.publicKey() ?: deviceKeys.createKey()
                AppDeviceDto(publicKey = publicKey, name = config.deviceName, installId = settings.installId())
            }
    }
