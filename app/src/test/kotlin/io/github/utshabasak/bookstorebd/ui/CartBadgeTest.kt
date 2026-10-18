package io.github.utshabasak.bookstorebd.ui

import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.CartItem
import org.junit.Assert.assertEquals
import org.junit.Test

class CartBadgeTest {
    private val book = Book(id = "b1", title = "Deyal", author = "Humayun Ahmed", price = 450.0, stock = 5)

    @Test
    fun `the cart's badge counts the copies checkout would order, not the sold-out books waiting`() {
        val items = listOf(CartItem(book, 2), CartItem(book.copy(id = "b2", stock = 0), 1))
        assertEquals(2, cartBadge(items))
        assertEquals(0, cartBadge(listOf(CartItem(book.copy(stock = 0), 3))))
    }
}
