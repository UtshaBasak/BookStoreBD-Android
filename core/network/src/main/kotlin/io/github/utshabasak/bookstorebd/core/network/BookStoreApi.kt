package io.github.utshabasak.bookstorebd.core.network

import io.github.utshabasak.bookstorebd.core.network.dto.AppConfigDto
import io.github.utshabasak.bookstorebd.core.network.dto.BookDto
import io.github.utshabasak.bookstorebd.core.network.dto.BookRequestStatusDto
import io.github.utshabasak.bookstorebd.core.network.dto.CataloguePageDto
import io.github.utshabasak.bookstorebd.core.network.dto.CheckPromoRequest
import io.github.utshabasak.bookstorebd.core.network.dto.CheckPromoResponse
import io.github.utshabasak.bookstorebd.core.network.dto.CreateOrderRequest
import io.github.utshabasak.bookstorebd.core.network.dto.CreateOrderResponse
import io.github.utshabasak.bookstorebd.core.network.dto.ForYouDto
import io.github.utshabasak.bookstorebd.core.network.dto.HomeSectionsDto
import io.github.utshabasak.bookstorebd.core.network.dto.MessageDto
import io.github.utshabasak.bookstorebd.core.network.dto.OwnProfileDto
import io.github.utshabasak.bookstorebd.core.network.dto.PasswordCheckDto
import io.github.utshabasak.bookstorebd.core.network.dto.PasswordCheckRequest
import io.github.utshabasak.bookstorebd.core.network.dto.PopularSearchesDto
import io.github.utshabasak.bookstorebd.core.network.dto.QuantityRequest
import io.github.utshabasak.bookstorebd.core.network.dto.RefreshRequest
import io.github.utshabasak.bookstorebd.core.network.dto.ResetPasswordRequest
import io.github.utshabasak.bookstorebd.core.network.dto.ReviewSummaryDto
import io.github.utshabasak.bookstorebd.core.network.dto.SellerShopDto
import io.github.utshabasak.bookstorebd.core.network.dto.SendOtpRequest
import io.github.utshabasak.bookstorebd.core.network.dto.SignOutOthersDto
import io.github.utshabasak.bookstorebd.core.network.dto.SuggestResponseDto
import io.github.utshabasak.bookstorebd.core.network.dto.VerifyOtpRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * The API under /api/v1, as the app uses it. Paths are relative to
 * [NetworkConfig.apiBaseUrl]. Requests carry the access token when there is
 * one, and a 401 refreshes the session once and retries (TokenAuthenticator).
 */
@Suppress("TooManyFunctions")
interface BookStoreApi {
    @GET("app/config")
    suspend fun appConfig(): AppConfigDto

    // ---------------------------------------------------------------- catalogue

    @GET("filter/sections")
    suspend fun homeSections(): HomeSectionsDto

    /**
     * One page of the catalogue. [filters] holds the named parameters
     * (search, bookType, sort, page and so on); categories repeat, so they
     * travel separately.
     */
    @GET("filter/booklist")
    suspend fun catalogue(
        @QueryMap filters: Map<String, String>,
        @Query("category") categories: List<String>,
    ): CataloguePageDto

    @GET("filter/suggest")
    suspend fun suggest(
        @Query("q") query: String,
    ): SuggestResponseDto

    @GET("filter/popular-searches")
    suspend fun popularSearches(): PopularSearchesDto

    /** Books in the order given, for Recently viewed. */
    @GET("filter/by-ids")
    suspend fun booksByIds(
        @Query("ids") commaSeparatedIds: String,
    ): List<BookDto>

    @GET("filter/for-you")
    suspend fun forYou(
        @Query("seen") commaSeparatedIds: String?,
    ): ForYouDto

    @GET("book/{id}")
    suspend fun book(
        @Path("id") id: String,
    ): BookDto

    @GET("review/{bookId}")
    suspend fun reviews(
        @Path("bookId") bookId: String,
    ): ReviewSummaryDto

    @GET("user/shop/{username}")
    suspend fun shop(
        @Path("username") username: String,
    ): SellerShopDto

    // ------------------------------------------------- "notify me when it is back"

    @GET("book/{id}/request")
    suspend fun bookRequest(
        @Path("id") id: String,
    ): BookRequestStatusDto

    @POST("book/{id}/request")
    suspend fun requestBook(
        @Path("id") id: String,
    ): BookRequestStatusDto

    @DELETE("book/{id}/request")
    suspend fun withdrawBookRequest(
        @Path("id") id: String,
    ): BookRequestStatusDto

    // ----------------------------------------------------------- cart and wishlist

    @GET("cart")
    suspend fun cart(): List<BookDto>

    @POST("cart/add/{id}")
    suspend fun addToCart(
        @Path("id") id: String,
        @Body body: QuantityRequest,
    ): List<BookDto>

    @PATCH("cart/{id}")
    suspend fun setCartQuantity(
        @Path("id") id: String,
        @Body body: QuantityRequest,
    ): List<BookDto>

    @POST("cart/remove/{id}")
    suspend fun removeFromCart(
        @Path("id") id: String,
    ): List<BookDto>

    @POST("cart/clear")
    suspend fun clearCart(): MessageDto

    @GET("wishlist")
    suspend fun wishlist(): List<BookDto>

    @POST("wishlist/add/{id}")
    suspend fun addToWishlist(
        @Path("id") id: String,
    ): List<BookDto>

    @POST("wishlist/remove/{id}")
    suspend fun removeFromWishlist(
        @Path("id") id: String,
    ): List<BookDto>

    // ---------------------------------------------------------------- checkout

    @POST("order/promo")
    suspend fun checkPromo(
        @Body body: CheckPromoRequest,
    ): CheckPromoResponse

    @POST("order/decrease-stock")
    suspend fun placeOrder(
        @Body body: CreateOrderRequest,
    ): CreateOrderResponse

    @GET("user/profile")
    suspend fun ownProfile(
        @Query("email") email: String,
    ): OwnProfileDto

    // ------------------------------------- codes and passwords (no session needed)

    @POST("auth/send-otp")
    suspend fun sendCode(
        @Body body: SendOtpRequest,
    ): MessageDto

    @POST("auth/verify-otp")
    suspend fun verifyCode(
        @Body body: VerifyOtpRequest,
    ): MessageDto

    @POST("auth/reset-password")
    suspend fun resetPassword(
        @Body body: ResetPasswordRequest,
    ): MessageDto

    @POST("auth/password-check")
    suspend fun passwordCheck(
        @Body body: PasswordCheckRequest,
    ): PasswordCheckDto

    @POST("app/auth/logout-others")
    suspend fun signOutOthers(
        @Body body: RefreshRequest,
    ): SignOutOthersDto
}
