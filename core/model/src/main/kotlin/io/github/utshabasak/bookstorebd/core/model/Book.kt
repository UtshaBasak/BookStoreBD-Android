package io.github.utshabasak.bookstorebd.core.model

/** New from a publisher, or second-hand. */
enum class BookType { New, Old }

/** A listing, as cards and pages show it. Prices are in taka. */
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val publisher: String = "",
    val language: String = "",
    val isbn: String = "",
    val pages: Int? = null,
    val description: String = "",
    val categories: List<String> = emptyList(),
    val type: BookType = BookType.New,
    /** For a second-hand copy: "like new", "good", "fair" and so on. */
    val condition: String? = null,
    val conditionDetails: String? = null,
    /** Absolute addresses; lists carry only the first, the detail page the whole gallery. */
    val images: List<String> = emptyList(),
    val sellerEmail: String = "",
    val stock: Int = 0,
    /** The listed price, struck through when there is a discount. */
    val price: Double,
    /** What a copy costs: [price] less the seller's discount. */
    val salePrice: Double = price,
    val discountPercent: Int = 0,
    val ratingAverage: Double = 0.0,
    val ratingCount: Int = 0,
    val viewCount: Int = 0,
    val wishlistCount: Int = 0,
) {
    val inStock: Boolean get() = stock > 0
    val hasDeal: Boolean get() = discountPercent > 0 && salePrice < price
    val cover: String? get() = images.firstOrNull()
}

/** A book's own page: the book and a few others like it. */
data class BookDetail(
    val book: Book,
    val related: List<Book>,
)

/** A book in the cart, with how many copies. */
data class CartItem(
    val book: Book,
    val quantity: Int,
    /** The quantity was lowered to what is left in stock. */
    val adjusted: Boolean = false,
) {
    val lineTotal: Double get() = book.salePrice * quantity
}
