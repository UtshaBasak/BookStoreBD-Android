package io.github.utshabasak.bookstorebd.core.model

/** One earlier turn of a conversation with the shopping assistant, sent back with the next question. */
data class ChatTurn(
    val fromUser: Boolean,
    val text: String,
)

/** A page of the shop the assistant points to: always a path on the website, such as `/filter?deals=1`. */
data class AssistantLink(
    val href: String,
    val label: String,
) {
    /** A path on the shop's own site; anything else is never shown. */
    val isSitePath: Boolean get() = href.startsWith("/") && !href.startsWith("//")
}

/** One of the person's own orders, as the assistant explains it. */
data class AssistantOrder(
    val orderNumber: String,
    val placedAt: String?,
    /** The status in the language asked for. */
    val statusLabel: String,
    /** 0-4 along Order Confirmed to Delivered; -1 when cancelled. */
    val stage: Int,
    val explanation: String,
    val books: List<AssistantOrderBook>,
    val total: Double,
    val canCancel: Boolean,
    val returnableUntil: String?,
    val link: String,
) {
    val cancelled: Boolean get() = stage < 0
}

data class AssistantOrderBook(
    val title: String,
    val quantity: Int,
)

/** Someone to talk to: a book's seller, or the shop. Nothing is sent until the person chooses. */
data class AssistantHandover(
    val toSeller: Boolean,
    /** The account a chat opens with; null when there is no chat account to reach. */
    val chatWith: String?,
    val name: String,
    val bookTitle: String?,
    /** The shop's e-mail address, for the shop. */
    val email: String?,
)

/** A book the shop does not have, offered for the Wanted board. Posted only if the person confirms. */
data class AssistantWantedOffer(
    val title: String,
    val author: String,
)

/** The assistant's answer: plain text, and what the shop found for it. */
data class AssistantReply(
    /** Quoted back with feedback. */
    val requestId: String,
    val text: String,
    val books: List<Book> = emptyList(),
    val links: List<AssistantLink> = emptyList(),
    val orders: List<AssistantOrder> = emptyList(),
    val handover: AssistantHandover? = null,
    val wanted: AssistantWantedOffer? = null,
    /** Answered without the model, which was busy; the text says so. */
    val fallback: Boolean = false,
    /** The question was not about the shop. */
    val outOfScope: Boolean = false,
)

/** Why an answer was not helpful, as the server names the reasons. */
enum class FeedbackReason(
    val apiValue: String,
) {
    Wrong("wrong"),
    Unhelpful("unhelpful"),
    OffTopic("off-topic"),
    Slow("slow"),
    Other("other"),
}
