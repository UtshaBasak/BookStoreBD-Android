package io.github.utshabasak.bookstorebd.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PriceRequest(
    val price: Int,
)

@Serializable
data class StockRequest(
    val stock: Int,
)

/** `{ type: "none" }`, or a kind with its value. */
@Serializable
data class DiscountRequest(
    val type: String,
    val value: Int? = null,
)

@Serializable
data class BookMutationDto(
    val message: String = "",
    val book: BookDto? = null,
)

@Serializable
data class AddBookDto(
    val message: String = "",
    val book: BookDto? = null,
    /** How many people waiting on the Wanted board were told. */
    val waiting: Int = 0,
)

@Serializable
data class StatusRequest(
    val status: String,
)
