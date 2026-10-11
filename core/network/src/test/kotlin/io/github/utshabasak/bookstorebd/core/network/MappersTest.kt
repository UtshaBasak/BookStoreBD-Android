package io.github.utshabasak.bookstorebd.core.network

import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.network.dto.BookDto
import org.junit.Test

class MappersTest {
    private val images = ImageUrls("https://shop.example/")
    private val mappers = Mappers(images)

    @Test
    fun `resolves every kind of picture address the API returns`() {
        assertThat(images.resolve("https://res.cloudinary.com/x/image/upload/v1/a.webp"))
            .isEqualTo("https://res.cloudinary.com/x/image/upload/v1/a.webp")
        assertThat(images.resolve("/api/book/b1/cover/0")).isEqualTo("https://shop.example/api/book/b1/cover/0")
        assertThat(images.resolve("data:image/png;base64,AAA")).isEqualTo("data:image/png;base64,AAA")
        assertThat(images.resolve("javascript:alert(1)")).isNull()
        assertThat(images.resolve("//evil.example/a.png")).isNull()
        assertThat(images.resolve("http://insecure.example/a.png")).isNull()
        assertThat(images.resolve(null)).isNull()
    }

    @Test
    fun `asks Cloudinary for the width drawn, as the website does`() {
        val original = "https://res.cloudinary.com/x/image/upload/v1/books/a.webp"
        val upload = "https://res.cloudinary.com/x/image/upload"
        assertThat(ImageUrls.sized(original, 400)).isEqualTo("$upload/f_auto,q_auto:eco,c_limit,w_400/v1/books/a.webp")
        assertThat(ImageUrls.sized(original, 800)).isEqualTo("$upload/f_auto,q_auto,c_limit,w_800/v1/books/a.webp")
        assertThat(ImageUrls.sized("https://shop.example/api/book/b1/cover/0", 400)).isEqualTo("https://shop.example/api/book/b1/cover/0")
        val already = "https://res.cloudinary.com/x/image/upload/w_100/v1/a.webp"
        assertThat(ImageUrls.sized(already, 400)).isEqualTo(already)
    }

    @Test
    fun `maps a second-hand book on sale, falling back to the list price without a sale price`() {
        val book =
            mappers.book(
                BookDto(
                    id = "b1",
                    title = "গল্পগুচ্ছ",
                    bookType = "old",
                    condition = "fair",
                    price = 800.0,
                    salePrice = 280.0,
                    discountPercent = 65.0,
                    stock = 0,
                    images = listOf("/api/book/b1/cover/0", "not a url"),
                ),
            )
        assertThat(book.type).isEqualTo(BookType.Old)
        assertThat(book.hasDeal).isTrue()
        assertThat(book.inStock).isFalse()
        assertThat(book.images).containsExactly("https://shop.example/api/book/b1/cover/0")

        val plain = mappers.book(BookDto(id = "b2", price = 500.0))
        assertThat(plain.salePrice).isEqualTo(500.0)
        assertThat(plain.hasDeal).isFalse()
    }

    @Test
    fun `reads cart quantities, never below one`() {
        val cart = mappers.cart(listOf(BookDto(id = "b1", price = 100.0, cartQuantity = 3), BookDto(id = "b2", price = 50.0)))
        assertThat(cart.map { it.quantity }).containsExactly(3, 1).inOrder()
        assertThat(cart.sumOf { it.lineTotal }).isEqualTo(350.0)
    }
}
