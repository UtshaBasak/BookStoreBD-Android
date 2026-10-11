package io.github.utshabasak.bookstorebd.core.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.utshabasak.bookstorebd.core.common.BookStoreDispatchers
import io.github.utshabasak.bookstorebd.core.common.Dispatcher
import io.github.utshabasak.bookstorebd.core.model.ImageUpload
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt

/** How large a picture is ever shown, so nothing bigger is sent. */
enum class ImagePurpose(
    val maxSide: Int,
) {
    Avatar(AVATAR_SIDE),
    Banner(PHOTO_SIDE),
    Photo(PHOTO_SIDE),
}

private const val AVATAR_SIDE = 512
private const val PHOTO_SIDE = 1600

/**
 * Turns a picture the person chose into bytes ready to upload, scaled to the
 * largest size it will be shown at and saved as a JPEG, as the website does:
 * a phone photograph of several megabytes usually becomes a few hundred
 * kilobytes, which matters on a mobile connection. The original is kept when
 * it is already smaller.
 */
@Singleton
class ImagePreparer
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @Dispatcher(BookStoreDispatchers.IO) private val io: CoroutineDispatcher,
    ) {
        suspend fun prepare(
            uri: Uri,
            purpose: ImagePurpose,
        ): ImageUpload? =
            withContext(io) {
                val resolver = context.contentResolver
                val original = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return@withContext null
                val type = resolver.getType(uri) ?: "image/jpeg"
                val scaled = runCatching { scale(uri, purpose.maxSide) }.getOrNull()
                if (scaled != null && scaled.size < original.size) {
                    ImageUpload(scaled, "image/jpeg", "${purpose.name.lowercase()}.jpg")
                } else {
                    ImageUpload(original, type, "${purpose.name.lowercase()}.${type.substringAfter('/')}")
                }
            }

        private fun scale(
            uri: Uri,
            maxSide: Int,
        ): ByteArray {
            val bitmap = decode(uri)
            val factor = min(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
            val resized =
                if (factor < 1f) {
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * factor).roundToInt(), (bitmap.height * factor).roundToInt(), true)
                } else {
                    bitmap
                }
            return ByteArrayOutputStream().use { out ->
                resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
        }

        private fun decode(uri: Uri): Bitmap =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }

        private companion object {
            const val JPEG_QUALITY = 85
        }
    }
