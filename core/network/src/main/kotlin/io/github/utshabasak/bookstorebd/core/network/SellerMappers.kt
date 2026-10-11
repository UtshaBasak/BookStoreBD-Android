package io.github.utshabasak.bookstorebd.core.network

import io.github.utshabasak.bookstorebd.core.model.Discount
import io.github.utshabasak.bookstorebd.core.model.DiscountKind
import io.github.utshabasak.bookstorebd.core.model.Listing
import io.github.utshabasak.bookstorebd.core.model.PayoutState
import io.github.utshabasak.bookstorebd.core.model.Sale
import io.github.utshabasak.bookstorebd.core.network.dto.BookDto
import io.github.utshabasak.bookstorebd.core.network.dto.OrderLineDto

/** A seller's listings and sales, from the API's shapes to the app's. */
class SellerMappers(
    private val books: Mappers,
    private val orders: AccountMappers,
) {
    fun listing(
        dto: BookDto,
        waiting: Int,
    ): Listing =
        Listing(
            book = books.book(dto),
            discount = Discount(DiscountKind.from(dto.discountType), if (dto.discountType.isNullOrBlank()) 0 else dto.discountValue),
            createdAt = dto.createdAt,
            waiting = waiting,
        )

    fun sale(dto: OrderLineDto): Sale =
        Sale(
            line = orders.line(dto),
            buyerEmail = dto.buyerEmail,
            buyerNote = dto.buyerNote,
            payout = PayoutState.from(dto.payoutState),
            payableFrom = dto.payableFrom,
            paidAt = dto.sellerPaidAt,
            payoutRef = dto.sellerPayoutRef,
        )
}
