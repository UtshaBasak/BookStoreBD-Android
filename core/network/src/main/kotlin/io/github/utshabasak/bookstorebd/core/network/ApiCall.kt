package io.github.utshabasak.bookstorebd.core.network

import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.network.dto.ApiErrorDto
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException

/**
 * Runs one API call and turns whatever happens into an [Outcome]: the value,
 * or an [AppError] a screen can explain. Cancellation is never swallowed.
 *
 * [credentials] is for sign-in and the like, where a 401 means the details
 * were wrong - the server says so in its message - rather than that a session
 * ended.
 *
 * Each caught exception becomes the error it stands for, so nothing a screen
 * could use is lost.
 */
@Suppress("SwallowedException")
suspend fun <T> apiCall(
    json: Json,
    credentials: Boolean = false,
    block: suspend () -> T,
): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (http: HttpException) {
        Outcome.Failure(errorFrom(json, http.code(), http.response()?.errorBody()?.string(), credentials))
    } catch (timeout: InterruptedIOException) {
        Outcome.Failure(if (timeout.message?.contains("timeout", ignoreCase = true) == true) AppError.Timeout else AppError.Offline)
    } catch (noHost: UnknownHostException) {
        Outcome.Failure(AppError.Offline)
    } catch (io: IOException) {
        Outcome.Failure(AppError.Offline)
    } catch (
        @Suppress("TooGenericExceptionCaught") other: RuntimeException,
    ) {
        Outcome.Failure(AppError.Unexpected(other))
    }

/** Reads the server's error body: `{ message, code?, errors? }`. */
fun errorFrom(
    json: Json,
    status: Int,
    body: String?,
    credentials: Boolean = false,
): AppError {
    val parsed = body?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString<ApiErrorDto>(it) }.getOrNull() }
    val message = parsed?.message?.takeIf { it.isNotBlank() }
    return when {
        status == HTTP_UPGRADE_REQUIRED || parsed?.code == "app-update-required" -> {
            AppError.UpdateRequired
        }

        status == HTTP_UNAUTHORIZED && !credentials -> {
            AppError.SignInRequired
        }

        status == HTTP_TOO_MANY -> {
            AppError.RateLimited(message)
        }

        parsed?.code == "captcha" -> {
            AppError.CaptchaRequired
        }

        status >= HTTP_SERVER_ERROR -> {
            AppError.Server(status)
        }

        else -> {
            AppError.Rejected(
                status = status,
                message = message,
                code = parsed?.code,
                fieldErrors = parsed?.errors.orEmpty().associate { it.path.substringAfterLast('.') to it.message },
            )
        }
    }
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_UPGRADE_REQUIRED = 426
private const val HTTP_TOO_MANY = 429
private const val HTTP_SERVER_ERROR = 500
