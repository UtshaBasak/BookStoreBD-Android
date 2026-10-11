package io.github.utshabasak.bookstorebd.core.network

import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.CancelledBy
import io.github.utshabasak.bookstorebd.core.model.Gender
import io.github.utshabasak.bookstorebd.core.model.Invitee
import io.github.utshabasak.bookstorebd.core.model.Invites
import io.github.utshabasak.bookstorebd.core.model.Order
import io.github.utshabasak.bookstorebd.core.model.OrderDelivery
import io.github.utshabasak.bookstorebd.core.model.OrderLine
import io.github.utshabasak.bookstorebd.core.model.OrderStatus
import io.github.utshabasak.bookstorebd.core.model.OwnProfile
import io.github.utshabasak.bookstorebd.core.model.Rating
import io.github.utshabasak.bookstorebd.core.model.ReturnStatus
import io.github.utshabasak.bookstorebd.core.model.Review
import io.github.utshabasak.bookstorebd.core.model.ReviewBlock
import io.github.utshabasak.bookstorebd.core.model.ReviewPage
import io.github.utshabasak.bookstorebd.core.model.ReviewReply
import io.github.utshabasak.bookstorebd.core.model.ReviewSummary
import io.github.utshabasak.bookstorebd.core.model.WantedItem
import io.github.utshabasak.bookstorebd.core.model.WantedStatus
import io.github.utshabasak.bookstorebd.core.network.dto.InviteListDto
import io.github.utshabasak.bookstorebd.core.network.dto.OrderDetailDto
import io.github.utshabasak.bookstorebd.core.network.dto.OrderLineDto
import io.github.utshabasak.bookstorebd.core.network.dto.OwnProfileDto
import io.github.utshabasak.bookstorebd.core.network.dto.ReviewDto
import io.github.utshabasak.bookstorebd.core.network.dto.ReviewPageDto
import io.github.utshabasak.bookstorebd.core.network.dto.WantedItemDto
import java.net.URLEncoder

/** Wire types to models for the account, orders, reviews and the Wanted board. */
class AccountMappers(
    private val images: ImageUrls,
    siteUrl: String,
) {
    private val origin = siteUrl.trimEnd('/')

    fun profile(dto: OwnProfileDto): OwnProfile =
        OwnProfile(
            username = dto.username,
            email = dto.email,
            pictureUrl = picture(dto.email, dto.profilePicture),
            buyerBannerUrl = images.resolve(dto.buyerBanner),
            sellerBannerUrl = images.resolve(dto.sellerBanner),
            sellerRating = Rating(dto.sellerRating.average, dto.sellerRating.count),
            address = dto.address.orEmpty(),
            phone = dto.phone.orEmpty(),
            bkashMerchant = dto.bkashMerchant.orEmpty(),
            dateOfBirth = dto.dateOfBirth.orEmpty(),
            gender = Gender.entries.firstOrNull { it.apiValue == dto.gender },
            twoFactor = dto.twoFactor,
            passwordSet = dto.passwordSet,
        )

    /**
     * A picture kept as a data URI is loaded from the avatar endpoint instead,
     * which images can cache; the version changes with the picture.
     */
    private fun picture(
        email: String,
        stored: String?,
    ): String? {
        if (stored.isNullOrBlank()) return null
        if (!stored.startsWith("data:")) return images.resolve(stored)
        val address = URLEncoder.encode(email, Charsets.UTF_8.name())
        return "$origin/api/user/$address/avatar?v=${stored.hashCode().toUInt()}"
    }

    fun invites(dto: InviteListDto): Invites = Invites(dto.items.map { Invitee(it.email, it.invitedAt, it.joined) }, dto.remainingToday)

    fun line(dto: OrderLineDto): OrderLine =
        OrderLine(
            id = dto.id,
            orderNumber = dto.orderNumber,
            status = OrderStatus.from(dto.status),
            bookId = dto.bookId,
            title = dto.title,
            author = dto.author,
            condition = dto.condition?.takeIf { it.isNotBlank() },
            type = if (dto.bookType == "old") BookType.Old else BookType.New,
            price = dto.price,
            listPrice = dto.listPrice ?: dto.price,
            quantity = dto.quantity.coerceAtLeast(1),
            sellerEmail = dto.sellerEmail,
            createdAt = dto.createdAt,
            deliveredAt = dto.deliveredAt,
            cancelledAt = dto.cancelledAt,
            cancelledBy =
                when (dto.cancelledBy) {
                    "buyer" -> CancelledBy.Buyer
                    "seller" -> CancelledBy.Seller
                    "admin" -> CancelledBy.Admin
                    "" -> null
                    else -> CancelledBy.Unknown
                },
            cancelReason = dto.cancelReason,
            returnStatus =
                when (dto.returnStatus) {
                    "pending" -> ReturnStatus.Pending
                    "approved" -> ReturnStatus.Approved
                    "rejected" -> ReturnStatus.Rejected
                    else -> null
                },
            returnableUntil = dto.returnableUntil,
            coverUrl = dto.bookId.takeIf { it.isNotBlank() }?.let { images.resolve("/api/book/$it/cover") },
        )

    /**
     * Lines grouped into orders, in the order the server sent them. Shipping
     * and discount repeat on every line and count once; cancelled lines are
     * left out of the totals.
     */
    fun orders(lines: List<OrderLineDto>): List<Order> =
        lines.groupBy { it.orderNumber }.map { (number, group) ->
            val first = group.first()
            val models = group.map(::line)
            val live = models.filterNot { it.isCancelled }
            val booksTotal = first.booksTotal ?: live.sumOf { it.lineTotal }
            val shipping = first.shippingCost ?: if (live.isEmpty()) 0.0 else first.shippingCharge
            val discount = if (live.isEmpty()) 0.0 else first.discount
            Order(
                orderNumber = number,
                status = orderStatus(models),
                placedAt = first.createdAt,
                lines = models,
                booksTotal = booksTotal,
                shipping = shipping,
                discount = discount,
                total = first.totalCost ?: (booksTotal + shipping - discount).coerceAtLeast(0.0),
                promo = first.promo?.takeIf { it.isNotBlank() },
                delivery = delivery(first),
            )
        }

    fun order(dto: OrderDetailDto): Order {
        val models = dto.books.map(::line)
        return Order(
            orderNumber = dto.orderNumber,
            status = OrderStatus.from(dto.status),
            placedAt = dto.createdAt ?: dto.books.firstOrNull()?.createdAt,
            lines = models,
            booksTotal = dto.booksTotal,
            shipping = dto.shippingCost,
            discount = dto.discount,
            total = dto.totalCost,
            promo = dto.promo?.takeIf { it.isNotBlank() },
            delivery =
                OrderDelivery(
                    name = dto.contactName,
                    phone = dto.contactPhone,
                    division = dto.deliveryDivision,
                    district = dto.deliveryDistrict,
                    address = dto.deliveryAddress,
                    note = dto.buyerNote,
                    paymentMethod = dto.paymentMethod,
                ),
            canCancel = dto.canCancel,
            statusOptions = dto.statusOptions.map(OrderStatus::from).distinct(),
            buyerEmail = dto.buyerEmail.ifBlank { dto.books.firstOrNull()?.buyerEmail.orEmpty() },
        )
    }

    /** An order's status is its least advanced live line's, or Cancelled when every line is. */
    private fun orderStatus(lines: List<OrderLine>): OrderStatus {
        val live = lines.filterNot { it.isCancelled }
        return live.minByOrNull { OrderStatus.steps.indexOf(it.status) }?.status ?: OrderStatus.Cancelled
    }

    private fun delivery(dto: OrderLineDto) =
        OrderDelivery(
            name = dto.contactName,
            phone = dto.contactPhone,
            division = dto.deliveryDivision,
            district = dto.deliveryDistrict,
            address = dto.deliveryAddress,
            note = dto.buyerNote,
            paymentMethod = dto.paymentMethod,
        )

    fun reviewPage(dto: ReviewPageDto): ReviewPage =
        ReviewPage(
            summary =
                ReviewSummary(
                    average = dto.average,
                    count = dto.count,
                    distribution = dto.distribution.takeIf { it.size == STARS } ?: List(STARS) { 0 },
                    reviews = dto.reviews.map(::review),
                ),
            mine = dto.mine?.let(::review),
            canReview = dto.canReview,
            block =
                when (dto.reason) {
                    "sign-in" -> ReviewBlock.SignIn
                    "own-listing" -> ReviewBlock.OwnListing
                    "own-shop" -> ReviewBlock.OwnShop
                    "not-purchased" -> ReviewBlock.NotPurchased
                    else -> null
                },
            isSeller = dto.isSeller,
        )

    fun review(dto: ReviewDto): Review =
        Review(
            id = dto.id,
            reviewerName = dto.reviewerName,
            rating = dto.rating,
            title = dto.title.orEmpty(),
            body = dto.body.orEmpty(),
            createdAt = dto.createdAt,
            reply = dto.reply?.let { ReviewReply(it.body, it.byName, it.at) },
            reviewerEmail = dto.reviewerEmail,
        )

    fun wanted(dto: WantedItemDto): WantedItem =
        WantedItem(
            id = dto.id,
            title = dto.title,
            author = dto.author.orEmpty(),
            isbn = dto.isbn.orEmpty(),
            details = dto.details.orEmpty(),
            count = dto.count,
            wantedByMe = dto.wantedByMe,
            status = if (dto.status == "found") WantedStatus.Found else WantedStatus.Open,
            foundBookId = dto.foundBook?.id,
            foundBookTitle = dto.foundBook?.title,
            createdAt = dto.createdAt,
        )

    private companion object {
        const val STARS = 5
    }
}
