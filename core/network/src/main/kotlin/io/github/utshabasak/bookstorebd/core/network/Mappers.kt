package io.github.utshabasak.bookstorebd.core.network

import io.github.utshabasak.bookstorebd.core.model.AdvertisedPromo
import io.github.utshabasak.bookstorebd.core.model.AppConfig
import io.github.utshabasak.bookstorebd.core.model.AppRelease
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.BookDetail
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.CartItem
import io.github.utshabasak.bookstorebd.core.model.CataloguePage
import io.github.utshabasak.bookstorebd.core.model.CategoryCount
import io.github.utshabasak.bookstorebd.core.model.CategoryGroup
import io.github.utshabasak.bookstorebd.core.model.DeliveryRules
import io.github.utshabasak.bookstorebd.core.model.Division
import io.github.utshabasak.bookstorebd.core.model.HomeSections
import io.github.utshabasak.bookstorebd.core.model.PasswordCheck
import io.github.utshabasak.bookstorebd.core.model.PlacedOrder
import io.github.utshabasak.bookstorebd.core.model.PopularWriter
import io.github.utshabasak.bookstorebd.core.model.Promo
import io.github.utshabasak.bookstorebd.core.model.Promotions
import io.github.utshabasak.bookstorebd.core.model.Review
import io.github.utshabasak.bookstorebd.core.model.ReviewReply
import io.github.utshabasak.bookstorebd.core.model.ReviewSummary
import io.github.utshabasak.bookstorebd.core.model.SellerHit
import io.github.utshabasak.bookstorebd.core.model.SellerShop
import io.github.utshabasak.bookstorebd.core.model.SessionUser
import io.github.utshabasak.bookstorebd.core.model.SiteLinks
import io.github.utshabasak.bookstorebd.core.model.SuggestedBook
import io.github.utshabasak.bookstorebd.core.model.Suggestions
import io.github.utshabasak.bookstorebd.core.network.dto.AppConfigDto
import io.github.utshabasak.bookstorebd.core.network.dto.BookDto
import io.github.utshabasak.bookstorebd.core.network.dto.CataloguePageDto
import io.github.utshabasak.bookstorebd.core.network.dto.CheckPromoResponse
import io.github.utshabasak.bookstorebd.core.network.dto.CountDto
import io.github.utshabasak.bookstorebd.core.network.dto.CreateOrderResponse
import io.github.utshabasak.bookstorebd.core.network.dto.HomeSectionsDto
import io.github.utshabasak.bookstorebd.core.network.dto.PasswordCheckDto
import io.github.utshabasak.bookstorebd.core.network.dto.ReviewSummaryDto
import io.github.utshabasak.bookstorebd.core.network.dto.SellerShopDto
import io.github.utshabasak.bookstorebd.core.network.dto.SessionUserDto
import io.github.utshabasak.bookstorebd.core.network.dto.SuggestResponseDto
import kotlin.math.roundToInt

/** Turns wire types into the app's models, resolving picture addresses on the way. */
class Mappers(
    private val images: ImageUrls,
) {
    fun book(dto: BookDto): Book =
        Book(
            id = dto.id,
            title = dto.title,
            author = dto.author,
            // Only what the seller filled in: blank, or an older listing's "N/A", is nothing to show.
            publisher = given(dto.publisher),
            language = given(dto.language),
            isbn = given(dto.isbn),
            pages = dto.pages?.takeIf { it > 0 },
            description = given(dto.desc),
            categories = dto.category,
            type = if (dto.bookType == "old") BookType.Old else BookType.New,
            // A copy's condition, which only a second-hand book has.
            condition = given(dto.condition).takeIf { dto.bookType == "old" && it.isNotEmpty() },
            conditionDetails = given(dto.conditionDetails).takeIf { dto.bookType == "old" && it.isNotEmpty() },
            images = dto.images.mapNotNull(images::resolve),
            sellerEmail = dto.sellerEmail,
            stock = dto.stock.coerceAtLeast(0),
            price = dto.price,
            salePrice = dto.salePrice ?: dto.price,
            discountPercent = dto.discountPercent.roundToInt(),
            ratingAverage = dto.ratingAverage,
            ratingCount = dto.ratingCount,
            viewCount = dto.viewCount,
            wishlistCount = dto.wishlistCount,
        )

    fun bookDetail(dto: BookDto): BookDetail = BookDetail(book(dto), dto.relatedBooks.map(::book))

    fun cart(items: List<BookDto>): List<CartItem> =
        items.map { CartItem(book(it), quantity = (it.cartQuantity ?: 1).coerceAtLeast(1), adjusted = it.cartAdjusted) }

    fun page(dto: CataloguePageDto): CataloguePage = CataloguePage(dto.items.map(::book), dto.total, dto.page, dto.pageCount)

    fun home(dto: HomeSectionsDto): HomeSections =
        HomeSections(
            deals = dto.deals.map(::book),
            trending = dto.trending.map(::book),
            popular = dto.popular.map(::book),
            bestsellers = dto.bestsellers.map(::book),
            topRated = dto.topRated.map(::book),
            budget = dto.budget.map(::book),
            discover = dto.discover.map(::book),
            writers = dto.writers.map { PopularWriter(it.name, it.books, it.sold) },
            categories = dto.categories.map(::count),
        )

    fun suggestions(dto: SuggestResponseDto): Suggestions =
        Suggestions(
            books =
                dto.books.map {
                    SuggestedBook(
                        id = it.id,
                        title = it.title,
                        author = it.author,
                        cover = images.resolve(it.cover),
                        price = it.price,
                        salePrice = it.salePrice ?: it.price,
                        discountPercent = it.discountPercent.roundToInt(),
                        inStock = it.inStock,
                    )
                },
            authors = dto.authors.map(::count),
            categories = dto.categories.map(::count),
            sellers = dto.sellers.map { SellerHit(it.username, images.resolve(it.avatar), it.books) },
        )

    fun shop(dto: SellerShopDto): SellerShop =
        SellerShop(
            id = dto.id,
            username = dto.username,
            email = dto.email,
            profilePicture = images.resolve(dto.profilePicture),
            banner = images.resolve(dto.sellerBanner),
            joinedAt = dto.joinedAt,
            books = dto.books,
            inStock = dto.inStock,
            sold = dto.sold,
            ratingAverage = dto.ratingAverage,
            ratingCount = dto.ratingCount,
            sellerRatingAverage = dto.sellerRating.average,
            sellerRatingCount = dto.sellerRating.count,
        )

    fun reviews(dto: ReviewSummaryDto): ReviewSummary =
        ReviewSummary(
            average = dto.average,
            count = dto.count,
            distribution = dto.distribution.takeIf { it.size == STARS } ?: List(STARS) { 0 },
            reviews =
                dto.reviews.map {
                    Review(
                        id = it.id,
                        reviewerName = it.reviewerName,
                        rating = it.rating,
                        title = it.title.orEmpty(),
                        body = it.body.orEmpty(),
                        createdAt = it.createdAt,
                        reply = it.reply?.let { reply -> ReviewReply(reply.body, reply.byName, reply.at) },
                    )
                },
        )

    fun user(dto: SessionUserDto): SessionUser = SessionUser(dto.id, dto.username, dto.email, isAdmin = dto.role == "admin")

    fun config(dto: AppConfigDto): AppConfig =
        AppConfig(
            minVersionCode = dto.android.minVersionCode,
            latestRelease =
                dto.android.latest?.let {
                    AppRelease(it.versionName, it.versionCode, it.apkUrl, it.apkSize, it.sha256, it.notes, it.pageUrl)
                },
            captchaSiteKey = dto.auth.captchaSiteKey,
            challengeUrl = dto.auth.challengeUrl,
            googleClientId = dto.auth.googleClientId?.takeIf { dto.auth.google },
            delivery =
                with(dto.shop.delivery) { DeliveryRules(insideDhaka, outsideDhaka, daysInsideDhaka, daysOutsideDhaka) },
            returnWindowDays = dto.shop.returnWindowDays,
            categoryGroups = dto.shop.categories.map { CategoryGroup(it.name, it.emoji, it.items) },
            divisions = dto.shop.divisions.map { Division(it.name, it.districts) },
            promotions =
                with(dto.shop.promotions) {
                    Promotions(
                        firstOrder = firstOrder?.let { AdvertisedPromo(it.code, it.description) },
                        freeDelivery = freeDelivery?.let { AdvertisedPromo(it.code, it.description, it.minBooksTotal) },
                    )
                },
            links =
                with(dto.links) {
                    // A server from before these were sent: the website's own addresses.
                    val home = site.trimEnd('/')
                    SiteLinks(
                        site = site,
                        privacy = privacy,
                        terms = terms,
                        returns = returns,
                        howItWorks = howItWorks,
                        about = about.ifBlank { "$home/about".takeIf { home.isNotEmpty() }.orEmpty() },
                        contact = contact.ifBlank { "$home/contact".takeIf { home.isNotEmpty() }.orEmpty() },
                        email = dto.contact.email,
                        location = dto.contact.location,
                    )
                },
        )

    fun promo(dto: CheckPromoResponse): Promo = Promo(dto.code, dto.description, dto.discount, dto.freeDelivery)

    fun order(dto: CreateOrderResponse): PlacedOrder =
        PlacedOrder(
            orderNumber = dto.orderNumber,
            shippingCharge = dto.shippingCharge,
            discount = dto.discount,
            promoMessage = dto.promoMessage,
            unavailable = dto.unavailable.map(::unavailableItem),
        )

    fun passwordCheck(dto: PasswordCheckDto): PasswordCheck = PasswordCheck(dto.ok, dto.problems, dto.message)

    private fun count(dto: CountDto) = CategoryCount(dto.name, dto.books)

    private companion object {
        const val STARS = 5
    }
}

/**
 * What a listing says about itself, for showing: a blank field, or one of the
 * "N/A" placeholders older listings were saved with, is nothing to show.
 */
internal fun given(value: String?): String {
    val text = value.orEmpty().trim()
    return if (NOT_GIVEN.matches(text)) "" else text
}

private val NOT_GIVEN = Regex("^n/?a$", RegexOption.IGNORE_CASE)
