package io.github.utshabasak.bookstorebd.core.network.dto

import kotlinx.serialization.Serializable

/*
 * The shopping assistant, as `server/shared/api.d.ts` describes it. Every
 * field has a default, so an older or a newer server still reads.
 */

@Serializable
data class AssistantConfigDto(
    val enabled: Boolean = false,
)

@Serializable
data class AssistantTurnDto(
    val role: String,
    val text: String,
)

@Serializable
data class AssistantRequest(
    val message: String,
    val history: List<AssistantTurnDto> = emptyList(),
    /** Left out entirely (null) unless the person lets the app remember viewed books. */
    val seen: List<String>? = null,
)

@Serializable
data class AssistantLinkDto(
    val href: String = "",
    val label: String = "",
)

@Serializable
data class AssistantOrderBookDto(
    val title: String = "",
    val quantity: Int = 1,
)

@Serializable
data class AssistantOrderDto(
    val orderNumber: String = "",
    val placedAt: String? = null,
    val status: String = "",
    val statusLabel: String = "",
    val stage: Int = 0,
    val explanation: String = "",
    val books: List<AssistantOrderBookDto> = emptyList(),
    val total: Double = 0.0,
    val canCancel: Boolean = false,
    val returnableUntil: String? = null,
    val link: String = "",
)

@Serializable
data class AssistantHandoverDto(
    val kind: String = "",
    val chatWith: String? = null,
    val name: String = "",
    val bookTitle: String? = null,
    val email: String? = null,
)

@Serializable
data class AssistantWantedOfferDto(
    val title: String = "",
    val author: String = "",
)

@Serializable
data class AssistantReplyDto(
    val requestId: String = "",
    val reply: String = "",
    val language: String = "",
    val books: List<BookDto> = emptyList(),
    val links: List<AssistantLinkDto> = emptyList(),
    val orders: List<AssistantOrderDto> = emptyList(),
    val handover: AssistantHandoverDto? = null,
    val wanted: AssistantWantedOfferDto? = null,
    val fallback: Boolean = false,
    val outOfScope: Boolean = false,
)

@Serializable
data class AssistantFeedbackRequest(
    val requestId: String,
    val helpful: Boolean,
    val reason: String? = null,
)
