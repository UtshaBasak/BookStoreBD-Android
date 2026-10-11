package io.github.utshabasak.bookstorebd.core.scanner

import com.google.common.truth.Truth.assertThat
import com.google.zxing.BarcodeFormat
import com.google.zxing.oned.EAN13Writer
import org.junit.Test

class BarcodeReaderTest {
    private val reader = BarcodeReader()

    /** A level barcode on a white page, as the camera would see it upright. */
    private fun page(code: String): BarcodeReader.Plane {
        val width = 640
        val height = 480
        val bars = EAN13Writer().encode(code, BarcodeFormat.EAN_13, 400, 160)
        val left = (width - bars.width) / 2
        val top = (height - bars.height) / 2
        val data =
            ByteArray(width * height) { i ->
                val x = i % width - left
                val y = i / width - top
                val black = x in 0 until bars.width && y in 0 until bars.height && bars[x, y]
                if (black) 0 else 255.toByte()
            }
        return BarcodeReader.Plane(data, width, height)
    }

    @Test
    fun `ISBNs are told from other barcodes by their prefix and check digit`() {
        assertThat(Isbn.fromBarcode("9789849044055")).isEqualTo("9789849044055")
        assertThat(Isbn.fromBarcode("9791032305690")).isEqualTo("9791032305690")
        assertThat(Isbn.fromBarcode("9789849044051")).isNull()
        assertThat(Isbn.fromBarcode("8901030865534")).isNull()
        assertThat(Isbn.fromBarcode("978984904405")).isNull()
        assertThat(Isbn.isValid13("978-984904405")).isFalse()
    }

    @Test
    fun `a book's barcode reads in a frame that is already upright`() {
        val page = page("9789849044055")

        assertThat(reader.read(page.data, page.width, page.height, 0)).isEqualTo(FrameRead.Book("9789849044055"))
    }

    @Test
    fun `a frame from a sensor turned a quarter is turned upright first`() {
        val page = page("9789849044055")
        // The phone is upright but its camera sensor lies on its side: the frame must turn 90 degrees to stand.
        val sensor = BarcodeReader.rotate(page.data, page.width, page.height, 270)

        assertThat(reader.read(sensor.data, sensor.width, sensor.height, 90)).isEqualTo(FrameRead.Book("9789849044055"))
        val other = BarcodeReader.rotate(page.data, page.width, page.height, 90)
        assertThat(reader.read(other.data, other.width, other.height, 270)).isEqualTo(FrameRead.Book("9789849044055"))
    }

    @Test
    fun `a barcode held on its side still reads`() {
        val page = page("9789849044055")
        val sideways = BarcodeReader.rotate(page.data, page.width, page.height, 90)

        assertThat(reader.read(sideways.data, sideways.width, sideways.height, 0)).isEqualTo(FrameRead.Book("9789849044055"))
    }

    @Test
    fun `another product's barcode is not taken for a book`() {
        val page = page("8901030865534")

        assertThat(reader.read(page.data, page.width, page.height, 0)).isEqualTo(FrameRead.OtherBarcode)
    }

    @Test
    fun `a frame with no barcode reads as nothing`() {
        val blank = ByteArray(640 * 480) { 200.toByte() }

        assertThat(reader.read(blank, 640, 480, 90)).isEqualTo(FrameRead.Nothing)
    }

    @Test
    fun `turning a plane four quarters returns it unchanged`() {
        val data = ByteArray(6) { it.toByte() }
        var plane = BarcodeReader.Plane(data, 3, 2)
        repeat(4) { plane = BarcodeReader.rotate(plane.data, plane.width, plane.height, 90) }

        assertThat(plane.data.toList()).isEqualTo(data.toList())
        assertThat(BarcodeReader.rotate(data, 3, 2, 90).data.map { it.toInt() }).containsExactly(3, 0, 4, 1, 5, 2).inOrder()
        assertThat(BarcodeReader.rotate(data, 3, 2, 270).data.map { it.toInt() }).containsExactly(2, 5, 1, 4, 0, 3).inOrder()
    }
}
