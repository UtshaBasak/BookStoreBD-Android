package io.github.utshabasak.bookstorebd.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.Review
import io.github.utshabasak.bookstorebd.core.model.ReviewBlock
import io.github.utshabasak.bookstorebd.core.model.ReviewPage

/** What the reviews section can ask for. */
data class ReviewActions(
    val onWrite: () -> Unit,
    val onReport: (Review, String) -> Unit,
    val onReply: (Review, String) -> Unit,
    val onDeleteReply: (Review) -> Unit,
    val onSignIn: () -> Unit = {},
)

/**
 * Reviews of a book, or ratings of a seller: the average and the spread of
 * stars, the person's own review first, then the rest with a "Verified
 * purchase" mark, sellers' replies, and ways to write, report or reply, as on
 * the website.
 */
@Composable
fun ReviewsSection(
    page: ReviewPage?,
    ofSeller: Boolean,
    viewerEmail: String?,
    actions: ReviewActions,
    modifier: Modifier = Modifier,
    shown: Int = SHOWN_REVIEWS,
) {
    val locale = currentLocale()
    var showAll by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(if (ofSeller) R.string.core_ui_reviews_seller_title else R.string.core_ui_reviews_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
        Summary(page, ofSeller)
        WriteButton(page, ofSeller, actions.onWrite)
        if (page != null && !page.canReview && page.mine == null) page.block?.let { WhyNoReview(it, ofSeller, actions.onSignIn) }
        val reviews = page?.summary?.reviews.orEmpty()
        val ordered = listOfNotNull(page?.mine) + reviews.filterNot { it.id == page?.mine?.id }
        val visible = if (showAll) ordered else ordered.take(shown)
        visible.forEach { review ->
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ReviewItem(
                review = review,
                own = review.id == page?.mine?.id || (viewerEmail != null && review.reviewerEmail.equals(viewerEmail, ignoreCase = true)),
                canReply = page?.isSeller == true,
                signedIn = viewerEmail != null,
                actions = actions,
            )
        }
        if (!showAll && ordered.size > shown) {
            TextButton(onClick = {
                showAll = true
            }) { Text(stringResource(R.string.core_ui_reviews_show_all, formatCount(ordered.size, locale))) }
        }
    }
}

@Composable
private fun Summary(
    page: ReviewPage?,
    ofSeller: Boolean,
) {
    val locale = currentLocale()
    val summary = page?.summary
    if (summary == null || summary.count == 0) {
        Text(
            stringResource(if (ofSeller) R.string.core_ui_reviews_none_seller else R.string.core_ui_reviews_none),
            style = MaterialTheme.typography.bodyMedium,
            color = BookStoreTheme.colors.textMuted,
        )
    } else {
        RatingLine(summary.average, summary.count)
        summary.distribution.withIndex().reversed().forEach { (index, count) ->
            val stars = index + 1
            val share = count.toFloat() / summary.count
            val spoken = pluralStringResource(R.plurals.core_ui_reviews_bar, stars, stars, formatCount(count, locale))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription =
                            spoken
                    },
            ) {
                Text("$stars★", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(32.dp))
                LinearProgressIndicator(progress = { share }, modifier = Modifier.weight(1f), drawStopIndicator = {})
                Text(
                    formatCount(count, locale),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 8.dp).width(32.dp),
                )
            }
        }
    }
}

@Composable
private fun WriteButton(
    page: ReviewPage?,
    ofSeller: Boolean,
    onWrite: () -> Unit,
) {
    if (page != null && (page.canReview || page.mine != null)) {
        OutlinedButton(onClick = onWrite, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(
                if (page.mine !=
                    null
                ) {
                    Icons.Outlined.Edit
                } else {
                    Icons.Outlined.RateReview
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(
                    when {
                        page.mine != null && ofSeller -> R.string.core_ui_reviews_edit_rating
                        page.mine != null -> R.string.core_ui_reviews_edit
                        ofSeller -> R.string.core_ui_reviews_rate
                        else -> R.string.core_ui_reviews_write
                    },
                ),
                Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ReviewItem(
    review: Review,
    own: Boolean,
    canReply: Boolean,
    signedIn: Boolean,
    actions: ReviewActions,
) {
    var menu by remember { mutableStateOf(false) }
    var reporting by rememberSaveable { mutableStateOf(false) }
    var replying by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                StarRating(review.rating)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (own) stringResource(R.string.core_ui_reviews_yours) else review.reviewerName,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Icon(
                        Icons.Outlined.Verified,
                        contentDescription = null,
                        tint = BookStoreTheme.colors.success,
                        modifier = Modifier.padding(start = 6.dp).size(14.dp),
                    )
                    Text(
                        stringResource(R.string.core_ui_reviews_verified) + " · " + formatDate(review.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = BookStoreTheme.colors.textMuted,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            if (signedIn && (!own || canReply)) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.core_ui_reviews_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (canReply) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (review.reply ==
                                                null
                                            ) {
                                                R.string.core_ui_reviews_reply
                                            } else {
                                                R.string.core_ui_reviews_edit_reply
                                            },
                                        ),
                                    )
                                },
                                onClick = {
                                    menu = false
                                    replying = true
                                },
                            )
                            if (review.reply != null) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.core_ui_reviews_remove_reply)) }, onClick = {
                                    menu = false
                                    actions.onDeleteReply(review)
                                })
                            }
                        }
                        if (!own) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.core_ui_reviews_report)) }, onClick = {
                                menu = false
                                reporting = true
                            })
                        }
                    }
                }
            }
        }
        if (review.title.isNotBlank()) Text(review.title, style = MaterialTheme.typography.titleSmall)
        if (review.body.isNotBlank()) Text(review.body, style = MaterialTheme.typography.bodyMedium)
        review.reply?.let { reply ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(10.dp),
            ) {
                Text(stringResource(R.string.core_ui_reviews_reply_from, reply.byName), style = MaterialTheme.typography.labelMedium)
                Text(reply.body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (reporting) {
        TextDialog(
            title = R.string.core_ui_reviews_report_title,
            body = R.string.core_ui_reviews_report_body,
            label = R.string.core_ui_reviews_report_reason,
            confirm = R.string.core_ui_reviews_report,
            initial = "",
            requireText = false,
            onConfirm = { reason ->
                reporting = false
                actions.onReport(review, reason)
            },
            onDismiss = { reporting = false },
        )
    }
    if (replying) {
        TextDialog(
            title = R.string.core_ui_reviews_reply_title,
            body = null,
            label = R.string.core_ui_reviews_reply_label,
            confirm = R.string.core_ui_reviews_reply_send,
            initial = review.reply?.body.orEmpty(),
            requireText = true,
            onConfirm = { text ->
                replying = false
                actions.onReply(review, text)
            },
            onDismiss = { replying = false },
        )
    }
}

@Composable
private fun TextDialog(
    title: Int,
    body: Int?,
    label: Int,
    confirm: Int,
    initial: String,
    requireText: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                body?.let { Text(stringResource(it)) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(TEXT_LIMIT) },
                    label = { Text(stringResource(label)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = !requireText || text.isNotBlank()) { Text(stringResource(confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_ui_reviews_cancel)) } },
    )
}

private const val SHOWN_REVIEWS = 5
private const val TEXT_LIMIT = 2000

/** Why there is no way to write a review, said plainly rather than left blank, as on the website. */
@Composable
private fun WhyNoReview(
    block: ReviewBlock,
    ofSeller: Boolean,
    onSignIn: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            val text =
                when (block) {
                    ReviewBlock.SignIn -> {
                        if (ofSeller) R.string.core_ui_reviews_sign_in_to_rate else R.string.core_ui_reviews_sign_in_to_review
                    }

                    ReviewBlock.NotPurchased -> {
                        if (ofSeller) R.string.core_ui_reviews_only_buyers_rate else R.string.core_ui_reviews_only_buyers_review
                    }

                    ReviewBlock.OwnListing -> {
                        R.string.core_ui_reviews_own_listing
                    }

                    ReviewBlock.OwnShop -> {
                        R.string.core_ui_reviews_own_shop
                    }
                }
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            if (block == ReviewBlock.SignIn) {
                TextButton(onClick = onSignIn, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.core_ui_reviews_sign_in))
                }
            }
        }
    }
}
