package io.github.utshabasak.bookstorebd.core.model

/**
 * Why something the app asked the server for did not work, in terms a screen
 * can act on. The server's own sentence is kept where it is meant for people.
 */
sealed interface AppError {
    /** No connection at all. */
    data object Offline : AppError

    /** The server took too long; on Render's free plan it may be waking up. */
    data object Timeout : AppError

    /** The session is over and the person must sign in again. */
    data object SignInRequired : AppError

    /** This build is too old for the API; the app must be updated. */
    data object UpdateRequired : AppError

    /** Too many attempts; try again later. */
    data class RateLimited(
        val message: String?,
    ) : AppError

    /** The bot check must be completed (again). */
    data object CaptchaRequired : AppError

    /** The server refused the request and said why, in [message]; [code] is its machine-readable reason, if any. */
    data class Rejected(
        val status: Int,
        val message: String?,
        val code: String? = null,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : AppError

    /** Something went wrong on the server's side. */
    data class Server(
        val status: Int,
    ) : AppError

    data class Unexpected(
        val cause: Throwable,
    ) : AppError
}

/** A result that is either a value or an [AppError]. */
sealed interface Outcome<out T> {
    data class Success<T>(
        val value: T,
    ) : Outcome<T>

    data class Failure(
        val error: AppError,
    ) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> =
    when (this) {
        is Outcome.Success -> Outcome.Success(transform(value))
        is Outcome.Failure -> this
    }

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value
