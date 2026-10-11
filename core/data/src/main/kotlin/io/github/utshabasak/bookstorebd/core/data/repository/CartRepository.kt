package io.github.utshabasak.bookstorebd.core.data.repository

import io.github.utshabasak.bookstorebd.core.data.cache.ResponseCache
import io.github.utshabasak.bookstorebd.core.data.session.SessionManager
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.CartItem
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.map
import io.github.utshabasak.bookstorebd.core.network.BookStoreApi
import io.github.utshabasak.bookstorebd.core.network.Mappers
import io.github.utshabasak.bookstorebd.core.network.apiCall
import io.github.utshabasak.bookstorebd.core.network.dto.BookDto
import io.github.utshabasak.bookstorebd.core.network.dto.QuantityRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val BOOK_LIST = ListSerializer(BookDto.serializer())

/**
 * The signed-in person's cart. Every change returns the whole cart from the
 * server, which is kept, so the cart shown is always the server's - quantities
 * already held within stock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CartRepository
    @Inject
    constructor(
        private val api: BookStoreApi,
        private val cache: ResponseCache,
        private val mappers: Mappers,
        private val json: Json,
        private val session: SessionManager,
    ) {
        /** The cart; empty when signed out. */
        val cart: Flow<List<CartItem>> =
            session.user.flatMapLatest { user ->
                if (user == null) {
                    flowOf(emptyList())
                } else {
                    cache.observe(ResponseCache.userKey(user.id, KEY), BOOK_LIST).map { mappers.cart(it.orEmpty()) }
                }
            }

        suspend fun refresh(): Outcome<Unit> = save { api.cart() }

        suspend fun add(
            bookId: String,
            quantity: Int = 1,
        ): Outcome<Unit> = save { api.addToCart(bookId, QuantityRequest(quantity)) }

        suspend fun setQuantity(
            bookId: String,
            quantity: Int,
        ): Outcome<Unit> = save { api.setCartQuantity(bookId, QuantityRequest(quantity)) }

        suspend fun remove(bookId: String): Outcome<Unit> = save { api.removeFromCart(bookId) }

        /** Empties the cart once an order is in. */
        suspend fun clear(): Outcome<Unit> {
            val user = session.currentUser() ?: return Outcome.Failure(AppError.SignInRequired)
            val result = apiCall(json) { api.clearCart() }
            if (result is Outcome.Success) cache.put(ResponseCache.userKey(user.id, KEY), BOOK_LIST, emptyList())
            return result.map { }
        }

        private suspend fun save(call: suspend () -> List<BookDto>): Outcome<Unit> {
            val user = session.currentUser() ?: return Outcome.Failure(AppError.SignInRequired)
            val result = apiCall(json) { call() }
            if (result is Outcome.Success) cache.put(ResponseCache.userKey(user.id, KEY), BOOK_LIST, result.value)
            return result.map { }
        }

        private companion object {
            const val KEY = "cart"
        }
    }

/** The signed-in person's wishlist, kept the same way as the cart. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WishlistRepository
    @Inject
    constructor(
        private val api: BookStoreApi,
        private val cache: ResponseCache,
        private val mappers: Mappers,
        private val json: Json,
        private val session: SessionManager,
    ) {
        val wishlist: Flow<List<Book>> =
            session.user.flatMapLatest { user ->
                if (user == null) {
                    flowOf(emptyList())
                } else {
                    cache.observe(ResponseCache.userKey(user.id, KEY), BOOK_LIST).map { list -> list.orEmpty().map(mappers::book) }
                }
            }

        /** The ids on the wishlist, for the heart on every card. */
        val ids: Flow<Set<String>> = wishlist.map { books -> books.mapTo(mutableSetOf()) { it.id } }

        suspend fun refresh(): Outcome<Unit> = save { api.wishlist() }

        suspend fun add(bookId: String): Outcome<Unit> = save { api.addToWishlist(bookId) }

        suspend fun remove(bookId: String): Outcome<Unit> = save { api.removeFromWishlist(bookId) }

        private suspend fun save(call: suspend () -> List<BookDto>): Outcome<Unit> {
            val user = session.currentUser() ?: return Outcome.Failure(AppError.SignInRequired)
            val result = apiCall(json) { call() }
            if (result is Outcome.Success) cache.put(ResponseCache.userKey(user.id, KEY), BOOK_LIST, result.value)
            return result.map { }
        }

        private companion object {
            const val KEY = "wishlist"
        }
    }
