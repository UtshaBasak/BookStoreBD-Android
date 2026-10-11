package io.github.utshabasak.bookstorebd.core.data.repository

import io.github.utshabasak.bookstorebd.core.data.session.SessionManager
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.ContactDetails
import io.github.utshabasak.bookstorebd.core.model.OrderRequest
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.PlacedOrder
import io.github.utshabasak.bookstorebd.core.model.Promo
import io.github.utshabasak.bookstorebd.core.model.map
import io.github.utshabasak.bookstorebd.core.network.BookStoreApi
import io.github.utshabasak.bookstorebd.core.network.Mappers
import io.github.utshabasak.bookstorebd.core.network.apiCall
import io.github.utshabasak.bookstorebd.core.network.dto.CheckPromoRequest
import io.github.utshabasak.bookstorebd.core.network.dto.CreateOrderRequest
import io.github.utshabasak.bookstorebd.core.network.dto.OrderItemDto
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checkout: cash on delivery, with delivery priced and promo codes valued by
 * the server. The cart is emptied only once the order is in, so a failed
 * checkout keeps it.
 */
@Singleton
class CheckoutRepository
    @Inject
    constructor(
        private val api: BookStoreApi,
        private val mappers: Mappers,
        private val json: Json,
        private val session: SessionManager,
        private val cart: CartRepository,
    ) {
        /** What the profile holds, to fill in the delivery form. */
        suspend fun contactDetails(): Outcome<ContactDetails> {
            val user = session.currentUser() ?: return Outcome.Failure(AppError.SignInRequired)
            return apiCall(json) { api.ownProfile(user.email) }.map {
                ContactDetails(name = it.username, phone = it.phone.orEmpty(), address = it.address.orEmpty())
            }
        }

        suspend fun checkPromo(
            code: String,
            booksTotal: Double,
        ): Outcome<Promo> = apiCall(json) { api.checkPromo(CheckPromoRequest(code.trim(), booksTotal)) }.map(mappers::promo)

        suspend fun placeOrder(request: OrderRequest): Outcome<PlacedOrder> {
            val result =
                apiCall(json) {
                    api.placeOrder(
                        CreateOrderRequest(
                            items = request.items.map { (bookId, quantity) -> OrderItemDto(bookId, quantity) },
                            promo = request.promo?.trim()?.takeIf { it.isNotEmpty() },
                            contactName = request.contactName.trim(),
                            contactPhone = request.contactPhone.trim(),
                            deliveryDivision = request.division,
                            deliveryDistrict = request.district,
                            deliveryAddress = request.address.trim(),
                            buyerNote = request.note.trim().takeIf { it.isNotEmpty() },
                        ),
                    )
                }
            if (result is Outcome.Success) cart.clear()
            return result.map(mappers::order)
        }
    }
