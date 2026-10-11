package io.github.utshabasak.bookstorebd.core.network

import io.github.utshabasak.bookstorebd.core.network.dto.AddBookDto
import io.github.utshabasak.bookstorebd.core.network.dto.BookDto
import io.github.utshabasak.bookstorebd.core.network.dto.BookMutationDto
import io.github.utshabasak.bookstorebd.core.network.dto.DiscountRequest
import io.github.utshabasak.bookstorebd.core.network.dto.MessageDto
import io.github.utshabasak.bookstorebd.core.network.dto.OrderLineDto
import io.github.utshabasak.bookstorebd.core.network.dto.PageDto
import io.github.utshabasak.bookstorebd.core.network.dto.PriceRequest
import io.github.utshabasak.bookstorebd.core.network.dto.StatusRequest
import io.github.utshabasak.bookstorebd.core.network.dto.StockRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.QueryMap

/** A seller's listings and the orders for them. Every change is checked against ownership by the server. */
interface SellerApi {
    @GET("book/seller/{email}")
    suspend fun listings(
        @Path("email") email: String,
    ): List<BookDto>

    /** Open requests for the seller's sold-out books, by book id. */
    @GET("book/requests/mine")
    suspend fun requestCounts(): Map<String, Int>

    @PUT("book/update-price/{id}")
    suspend fun setPrice(
        @Path("id") id: String,
        @Body body: PriceRequest,
    ): BookMutationDto

    @PUT("book/update-stock/{id}")
    suspend fun setStock(
        @Path("id") id: String,
        @Body body: StockRequest,
    ): BookMutationDto

    @PUT("book/discount/{id}")
    suspend fun setDiscount(
        @Path("id") id: String,
        @Body body: DiscountRequest,
    ): BookMutationDto

    @DELETE("book/{id}")
    suspend fun delete(
        @Path("id") id: String,
    ): MessageDto

    /** The listing's fields, and its photos as hosted addresses or as files. */
    @Multipart
    @POST("user/add-book")
    suspend fun addBook(
        @Part parts: List<MultipartBody.Part>,
    ): AddBookDto

    @GET("order/seller")
    suspend fun sales(
        @QueryMap query: Map<String, String>,
    ): PageDto<OrderLineDto>

    @PATCH("order/status/{orderNumber}")
    suspend fun setStatus(
        @Path("orderNumber") orderNumber: String,
        @Body body: StatusRequest,
    ): List<OrderLineDto>
}
