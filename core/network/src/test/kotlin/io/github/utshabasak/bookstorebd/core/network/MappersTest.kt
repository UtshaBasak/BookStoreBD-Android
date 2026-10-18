package io.github.utshabasak.bookstorebd.core.network

import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.OrderStatus
import io.github.utshabasak.bookstorebd.core.model.ReviewBlock
import io.github.utshabasak.bookstorebd.core.network.dto.BookDto
import io.github.utshabasak.bookstorebd.core.network.dto.OrderLineDto
import io.github.utshabasak.bookstorebd.core.network.dto.OwnProfileDto
import io.github.utshabasak.bookstorebd.core.network.dto.ReviewPageDto
import org.junit.Test

class MappersTest {
    private val images = ImageUrls("https://shop.example/")
    private val mappers = Mappers(images)
    private val accounts = AccountMappers(images, "https://shop.example")

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
    fun `shows only what a listing says - blank and older N-A fields are nothing, and a new book has no condition`() {
        val old =
            mappers.book(
                BookDto(
                    id = "b1",
                    bookType = "old",
                    publisher = "N/A",
                    language = " Bangla ",
                    isbn = "",
                    desc = "n/a",
                    condition = "NA",
                    conditionDetails = "Spine creased",
                    pages = 0,
                ),
            )
        assertThat(old.publisher).isEmpty()
        assertThat(old.language).isEqualTo("Bangla")
        assertThat(old.description).isEmpty()
        assertThat(old.condition).isNull()
        assertThat(old.conditionDetails).isEqualTo("Spine creased")
        assertThat(old.pages).isNull()

        val new = mappers.book(BookDto(id = "b2", bookType = "new", condition = "mint", conditionDetails = "As new"))
        assertThat(new.condition).isNull()
        assertThat(new.conditionDetails).isNull()
    }

    @Test
    fun `an order in a list stands and totals as the server says, for the whole order`() {
        val lines =
            listOf(
                line("l1", "Shipped").copy(
                    booksTotal = 900.0,
                    shippingCost = 70.0,
                    discount = 50.0,
                    totalCost = 920.0,
                    orderStatus = "Processing",
                    canCancel = false,
                ),
            )
        val order = accounts.orders(lines).single()
        assertThat(order.status).isEqualTo(OrderStatus.Processing)
        assertThat(order.total).isEqualTo(920.0)
        assertThat(order.canCancel).isFalse()
    }

    @Test
    fun `without the server's summary an order stands where its least advanced book does, its discount no more than the books`() {
        val order =
            accounts
                .orders(
                    listOf(
                        line("l1", "Shipped").copy(price = 100.0, discount = 300.0, shippingCharge = 70.0),
                        line("l2", "Order Confirmed").copy(price = 50.0, discount = 300.0, shippingCharge = 70.0),
                        line("l3", "Cancelled").copy(price = 500.0, discount = 300.0, shippingCharge = 70.0),
                    ),
                ).single()
        assertThat(order.status).isEqualTo(OrderStatus.Confirmed)
        assertThat(order.booksTotal).isEqualTo(150.0)
        assertThat(order.discount).isEqualTo(150.0)
        assertThat(order.total).isEqualTo(70.0)
        assertThat(order.canCancel).isFalse()
    }

    @Test
    fun `an order line's condition is only for a second-hand copy`() {
        assertThat(accounts.line(line("l1", "Delivered").copy(bookType = "new", condition = "mint")).condition).isNull()
        assertThat(accounts.line(line("l1", "Delivered").copy(bookType = "old", condition = "N/A")).condition).isNull()
        assertThat(accounts.line(line("l1", "Delivered").copy(bookType = "old", condition = "good")).condition).isEqualTo("good")
    }

    @Test
    fun `knows a review waits for delivery, and an address waiting for its code`() {
        assertThat(accounts.reviewPage(ReviewPageDto(reason = "not-delivered")).block).isEqualTo(ReviewBlock.NotDelivered)
        assertThat(accounts.profile(OwnProfileDto(email = "a@example.com", pendingEmail = "b@example.com")).pendingEmail)
            .isEqualTo("b@example.com")
        assertThat(accounts.profile(OwnProfileDto(email = "a@example.com", pendingEmail = "")).pendingEmail).isNull()
    }

    private fun line(
        id: String,
        status: String,
    ) = OrderLineDto(id = id, orderNumber = "BS-1", status = status, bookId = "b$id", title = "Book $id", price = 100.0)

    @Test
    fun `reads cart quantities, never below one`() {
        val cart = mappers.cart(listOf(BookDto(id = "b1", price = 100.0, cartQuantity = 3), BookDto(id = "b2", price = 50.0)))
        assertThat(cart.map { it.quantity }).containsExactly(3, 1).inOrder()
        assertThat(cart.sumOf { it.lineTotal }).isEqualTo(350.0)
    }
}
