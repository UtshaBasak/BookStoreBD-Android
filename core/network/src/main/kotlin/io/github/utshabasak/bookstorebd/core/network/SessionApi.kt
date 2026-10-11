package io.github.utshabasak.bookstorebd.core.network

import io.github.utshabasak.bookstorebd.core.network.dto.AppGoogleSignInRequest
import io.github.utshabasak.bookstorebd.core.network.dto.AppSignInRequest
import io.github.utshabasak.bookstorebd.core.network.dto.AppSignInResponse
import io.github.utshabasak.bookstorebd.core.network.dto.AppSignUpRequest
import io.github.utshabasak.bookstorebd.core.network.dto.AppVerifySignInRequest
import io.github.utshabasak.bookstorebd.core.network.dto.NonceDto
import io.github.utshabasak.bookstorebd.core.network.dto.RefreshRequest
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * The app's session endpoints (/api/v1/app/auth). On a client of their own,
 * with no access token and no authenticator, so a refresh can never trigger
 * another refresh.
 */
interface SessionApi {
    @POST("app/auth/signin")
    suspend fun signIn(
        @Body body: AppSignInRequest,
    ): AppSignInResponse

    @POST("app/auth/signin/verify")
    suspend fun verifySignIn(
        @Body body: AppVerifySignInRequest,
    ): AppSignInResponse

    @POST("app/auth/signup")
    suspend fun signUp(
        @Body body: AppSignUpRequest,
    ): AppSignInResponse

    @GET("app/auth/nonce")
    suspend fun nonce(): NonceDto

    @POST("app/auth/google")
    suspend fun googleSignIn(
        @Body body: AppGoogleSignInRequest,
    ): AppSignInResponse

    /**
     * Signed by the phone's key; see [DEVICE_SIGNATURE_HEADER]. A blocking
     * call rather than a suspending one: it runs inside OkHttp's authenticator,
     * where queueing it behind the requests that are waiting for it could
     * deadlock.
     */
    @POST(REFRESH_PATH)
    fun refresh(
        @Body body: RefreshRequest,
        @Header(DEVICE_TIMESTAMP_HEADER) timestamp: String,
        @Header(DEVICE_SIGNATURE_HEADER) signature: String,
    ): Call<AppSignInResponse>

    @POST("app/auth/logout")
    suspend fun signOut(
        @Body body: RefreshRequest,
    ): Response<Unit>

    companion object {
        const val REFRESH_PATH = "app/auth/refresh"
        const val DEVICE_TIMESTAMP_HEADER = "X-Device-Timestamp"
        const val DEVICE_SIGNATURE_HEADER = "X-Device-Signature"
    }
}
