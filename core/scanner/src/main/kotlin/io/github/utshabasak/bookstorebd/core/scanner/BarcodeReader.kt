package io.github.utshabasak.bookstorebd.core.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

/** What one camera frame showed. */
sealed interface FrameRead {
    data class Book(
        val isbn: String,
    ) : FrameRead

    /** A barcode, but another product's: the price sticker, or a code that is not a book's. */
    data object OtherBarcode : FrameRead

    data object Nothing : FrameRead
}

/**
 * Reads EAN-13 barcodes from a frame's brightness plane, on the phone itself.
 * The frame is first turned upright, so a barcode held level in the viewfinder
 * reads; then a quarter turn, so one held on its side reads too.
 */
internal class BarcodeReader {
    private val reader =
        MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.EAN_13)))
        }

    /** [luma] is [width] by [height], to be turned [rotation] degrees clockwise to stand upright. */
    fun read(
        luma: ByteArray,
        width: Int,
        height: Int,
        rotation: Int,
    ): FrameRead {
        val text =
            decode(rotate(luma, width, height, rotation))
                ?: decode(rotate(luma, width, height, (rotation + QUARTER) % FULL_TURN))
                ?: return FrameRead.Nothing
        return Isbn.fromBarcode(text)?.let(FrameRead::Book) ?: FrameRead.OtherBarcode
    }

    private fun decode(plane: Plane): String? {
        val source = PlanarYUVLuminanceSource(plane.data, plane.width, plane.height, 0, 0, plane.width, plane.height, false)
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: ReaderException) {
            null
        } finally {
            reader.reset()
        }
    }

    internal class Plane(
        val data: ByteArray,
        val width: Int,
        val height: Int,
    )

    internal companion object {
        const val QUARTER = 90
        const val HALF_TURN = 180
        const val THREE_QUARTERS = 270
        const val FULL_TURN = 360

        /** The plane turned clockwise by [degrees], a multiple of 90. */
        fun rotate(
            data: ByteArray,
            width: Int,
            height: Int,
            degrees: Int,
        ): Plane =
            when (degrees) {
                QUARTER -> {
                    Plane(ByteArray(data.size) { i -> data[(height - 1 - i % height) * width + i / height] }, height, width)
                }

                HALF_TURN -> {
                    Plane(ByteArray(data.size) { i -> data[data.size - 1 - i] }, width, height)
                }

                THREE_QUARTERS -> {
                    Plane(ByteArray(data.size) { i -> data[(i % height) * width + width - 1 - i / height] }, height, width)
                }

                else -> {
                    Plane(data, width, height)
                }
            }
    }
}

/** Hands each camera frame to [BarcodeReader]; anything found goes to [onRead]. */
internal class IsbnAnalyzer(
    private val onRead: (FrameRead) -> Unit,
) : ImageAnalysis.Analyzer {
    private val reader = BarcodeReader()

    override fun analyze(image: ImageProxy) {
        image.use {
            val read = reader.read(it.luma(), it.width, it.height, it.imageInfo.rotationDegrees)
            if (read != FrameRead.Nothing) onRead(read)
        }
    }

    /** The frame's first plane, its brightness, without the padding at the end of each row. */
    private fun ImageProxy.luma(): ByteArray {
        val plane = planes[0]
        val buffer = plane.buffer.apply { rewind() }
        val out = ByteArray(width * height)
        if (plane.rowStride == width) {
            buffer.get(out, 0, out.size)
        } else {
            for (row in 0 until height) {
                buffer.position(row * plane.rowStride)
                buffer.get(out, row * width, width)
            }
        }
        return out
    }
}
