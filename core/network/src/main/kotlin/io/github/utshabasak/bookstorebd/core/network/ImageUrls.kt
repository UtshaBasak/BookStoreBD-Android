package io.github.utshabasak.bookstorebd.core.network

import io.github.utshabasak.bookstorebd.core.model.sizedImageUrl

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
        /** See [sizedImageUrl]. */
        fun sized(
            url: String,
            widthPx: Int,
        ): String = sizedImageUrl(url, widthPx)
    }
}
