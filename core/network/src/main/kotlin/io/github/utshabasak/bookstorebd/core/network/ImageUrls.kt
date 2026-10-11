package io.github.utshabasak.bookstorebd.core.network

import java.net.URI

/**
 * Picture addresses as the API returns them, made ready to load.
 *
 * The API returns three kinds: a Cloudinary URL, a path on the site itself
 * (`/api/book/<id>/cover/0`), and, for records from before image hosting, a
 * base64 `data:` URI. Paths are made absolute; anything else unusable is
 * dropped, so a bad value shows the placeholder rather than an error.
 */
class ImageUrls(
    siteUrl: String,
) {
    private val origin = siteUrl.trimEnd('/')

    fun resolve(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        return when {
            trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("data:image/") -> trimmed
            trimmed.startsWith("/") && !trimmed.startsWith("//") -> "$origin$trimmed"
            else -> null
        }
    }

    companion object {
        private const val CLOUDINARY_HOST = "res.cloudinary.com"
        private const val UPLOAD = "/image/upload/"
        private const val THUMBNAIL_MAX = 400

        /**
         * Asks Cloudinary for the width actually drawn, in a modern format, as
         * the website does (client/src/utils/imageUrl.ts). Other addresses are
         * returned unchanged.
         */
        fun sized(
            url: String,
            widthPx: Int,
        ): String {
            val host = runCatching { URI(url).host }.getOrNull()
            val at = url.indexOf(UPLOAD)
            if (host != CLOUDINARY_HOST || at == -1) return url
            val head = url.substring(0, at + UPLOAD.length)
            val tail = url.substring(at + UPLOAD.length)
            // Already transformed: leave it alone.
            if (Regex("^[a-z]+_[^/]+/").containsMatchIn(tail)) return url
            val quality = if (widthPx <= THUMBNAIL_MAX) "q_auto:eco" else "q_auto"
            return "${head}f_auto,$quality,c_limit,w_$widthPx/$tail"
        }
    }
}
