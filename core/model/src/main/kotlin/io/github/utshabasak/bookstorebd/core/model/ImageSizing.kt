package io.github.utshabasak.bookstorebd.core.model

private const val CLOUDINARY_HOST = "res.cloudinary.com"
private const val UPLOAD = "/image/upload/"
private const val THUMBNAIL_MAX = 400
private val TRANSFORMED = Regex("^[a-z]+_[^/]+/")

/**
 * Asks Cloudinary for the width actually drawn, in a modern format, as the
 * website does (client/src/utils/imageUrl.ts): `f_auto` for WebP or AVIF,
 * `q_auto` for a quality the eye cannot fault (a little smaller for
 * thumbnails), and `c_limit` so a small picture is never stretched. Any other
 * address is returned unchanged.
 */
fun sizedImageUrl(
    url: String,
    widthPx: Int,
): String {
    val host = url.substringAfter("://", "").substringBefore('/')
    val at = url.indexOf(UPLOAD)
    if (!url.startsWith("https://") || host != CLOUDINARY_HOST || at == -1) return url
    val head = url.substring(0, at + UPLOAD.length)
    val tail = url.substring(at + UPLOAD.length)
    if (TRANSFORMED.containsMatchIn(tail)) return url
    val quality = if (widthPx <= THUMBNAIL_MAX) "q_auto:eco" else "q_auto"
    return "${head}f_auto,$quality,c_limit,w_$widthPx/$tail"
}
