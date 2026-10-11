package io.github.utshabasak.bookstorebd.core.network

import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Names the app on every request: `X-Client` is what the API's version check
 * reads (docs/API-VERSIONING.md on the server); the User-Agent shows in logs.
 */
class ClientHeadersInterceptor(
    private val config: NetworkConfig,
) : Interceptor {
    private val userAgent =
        "BookStoreBD-Android/${config.versionName} (${config.versionCode}; Android ${config.androidRelease}; ${config.deviceName.ascii()})"

    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain
                .request()
                .newBuilder()
                .header("X-Client", "android/${config.versionCode}")
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .build(),
        )

    // Header values must be ASCII; a phone's name may not be.
    private fun String.ascii(): String = filter { it.code in PRINTABLE_ASCII }.ifBlank { "Android phone" }

    private companion object {
        val PRINTABLE_ASCII = 0x20..0x7E
    }
}

/** The access token, held in memory by the session (core:data). */
fun interface AccessTokenProvider {
    fun currentAccessToken(): String?
}

/** Renews the session when the access token has expired (core:data). */
fun interface SessionRefresher {
    /**
     * Returns a fresh access token, or null when the session cannot be renewed
     * and the person has to sign in again. Called on OkHttp's thread; blocks.
     */
    fun refreshBlocking(failedToken: String?): String?
}

/** Adds the access token to requests, when there is one. */
class AccessTokenInterceptor(
    private val tokens: AccessTokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokens.currentAccessToken() ?: return chain.proceed(chain.request())
        return chain.proceed(chain.request().withToken(token))
    }
}

/**
 * On a 401, renews the session once and repeats the request. Concurrent
 * requests share one renewal (the refresher serialises them), and a request
 * that already failed after a renewal is not retried again.
 */
class TokenAuthenticator(
    private val refresher: SessionRefresher,
) : Authenticator {
    override fun authenticate(
        route: Route?,
        response: Response,
    ): Request? {
        if (response.priorResponse != null) return null
        val failed = response.request.header(AUTHORIZATION)?.removePrefix(BEARER)
        val fresh = refresher.refreshBlocking(failed) ?: return null
        return response.request.withToken(fresh)
    }
}

private const val AUTHORIZATION = "Authorization"
private const val BEARER = "Bearer "

private fun Request.withToken(token: String): Request = newBuilder().header(AUTHORIZATION, "$BEARER$token").build()
