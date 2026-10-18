package io.github.utshabasak.bookstorebd.core.model

/**
 * Something that happened which the person should hear about: an order step,
 * a return decided, a review, a price drop. The same list as the website's bell.
 */
data class AppNotification(
    val id: String,
    /** The server's type, such as `order-status`; [category] groups them. */
    val type: String,
    val title: String,
    val body: String,
    /** A path inside the site, such as `/order-tracking/ABC`; empty when there is nowhere to go. */
    val link: String,
    val read: Boolean,
    val createdAt: String,
) {
    val category: NotificationCategory get() = NotificationCategory.ofType(type)
}

/**
 * The kinds of notification people can turn off, as the website groups them.
 * Codes, sign-in alerts and changes to the account are [Account] and are
 * always sent.
 */
enum class NotificationCategory(
    val id: String,
    private val types: Set<String>,
) {
    Orders("orders", setOf("order-placed", "order-received", "order-status", "order-cancelled")),
    Returns("returns", setOf("return-requested", "return-decided")),
    Payouts("payouts", setOf("payout")),
    Reviews("reviews", setOf("review", "review-reply", "seller-review", "seller-review-reply")),
    Stock("stock", setOf("stock", "book-request")),

    // "selling-out": a book in the cart or wishlist is nearly gone.
    Deals("deals", setOf("deal", "price-drop", "back-in-stock", "selling-out")),
    Wanted("wanted", setOf("wanted-found")),
    Community("community", setOf("invite-joined")),
    Announcements("announcements", setOf("announcement")),
    Moderation("moderation", setOf("review-reported", "seller-review-reported")),
    Account("account", emptySet()),
    ;

    companion object {
        fun ofType(type: String): NotificationCategory = entries.firstOrNull { type in it.types } ?: Account

        fun ofId(id: String): NotificationCategory? = entries.firstOrNull { it.id == id }
    }
}

/** One switch row: whether this kind of notification arrives in the app (and on the phone), and by e-mail. */
data class NotificationChoice(
    val category: NotificationCategory,
    /** The server's English label and description, for categories the app does not know yet. */
    val label: String,
    val description: String,
    val emailAvailable: Boolean,
    val inApp: Boolean,
    val email: Boolean,
)

/** One conversation in the person's messages: who with, the last thing said, and how many are unread. */
data class Conversation(
    val email: String,
    val name: String,
    val avatarUrl: String?,
    val lastMessage: String,
    val lastMessageAt: String?,
    val unread: Int,
)

/** One message in a conversation; [imageUrl] when a photo is attached. */
data class ChatMessage(
    val id: String,
    val sender: String,
    val receiver: String,
    val text: String,
    val imageUrl: String?,
    val sentAt: String,
    val read: Boolean,
)
