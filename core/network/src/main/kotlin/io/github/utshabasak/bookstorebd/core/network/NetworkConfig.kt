package io.github.utshabasak.bookstorebd.core.network

/**
 * Where the API is and who is calling it, supplied by the app module, which
 * knows its build. The network layer never reads BuildConfig itself.
 */
data class NetworkConfig(
    /** The versioned API, ending in a slash: https://…/api/v1/ */
    val apiBaseUrl: String,
    /** The site's origin, for addresses the API returns as paths. */
    val siteUrl: String,
    val versionCode: Int,
    val versionName: String,
    /** "Samsung Galaxy A15", for the User-Agent and new-device e-mails. */
    val deviceName: String,
    val androidRelease: String,
    val logHttp: Boolean,
    /** The newest release's `update.json` on GitHub Releases; blank in builds that do not update themselves. */
    val releaseFeedUrl: String = "",
)
