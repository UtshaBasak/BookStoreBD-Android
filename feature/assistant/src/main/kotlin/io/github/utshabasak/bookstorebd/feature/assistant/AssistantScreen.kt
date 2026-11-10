package io.github.utshabasak.bookstorebd.feature.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.AssistantHandover
import io.github.utshabasak.bookstorebd.core.model.AssistantLink
import io.github.utshabasak.bookstorebd.core.model.AssistantOrder
import io.github.utshabasak.bookstorebd.core.model.AssistantReply
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.OrderStatus
import io.github.utshabasak.bookstorebd.core.ui.BookCard
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.errorMessage
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatDate
import io.github.utshabasak.bookstorebd.core.ui.formatTaka
import io.github.utshabasak.bookstorebd.core.ui.readableWidth

data class AssistantActions(
    val onBack: () -> Unit,
    val onInput: (String) -> Unit,
    val onSend: () -> Unit,
    /** Asks one of the starting questions. */
    val onAsk: (String) -> Unit,
    val onStartOver: () -> Unit,
    val onDismissError: () -> Unit,
    val onOpenPrivacy: () -> Unit,
    val onOpenBook: (Book) -> Unit,
    /** Opens a page of the shop by its path, in the app where the app has it. */
    val onOpenLink: (String) -> Unit,
    /** Opens the chat this answer offered; true to start the message with a summary of the questions. */
    val onChat: (AssistantHandover, Boolean) -> Unit,
    val onEmail: (String) -> Unit,
    val onSignIn: () -> Unit,
    val onFeedback: (Int, Boolean) -> Unit,
    val onAskWanted: (Int) -> Unit,
)

/** The conversation with the shopping assistant: the notice, the answers with what the shop found, and the question box. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    state: AssistantUiState,
    actions: AssistantActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val list = rememberLazyListState()
    // The newest message, or the thinking line, stays in view.
    LaunchedEffect(state.messages.size, state.pending) {
        val last = list.layoutInfo.totalItemsCount - 1
        if (last >= 0) list.animateScrollToItem(last)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_assistant_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_assistant_back))
                    }
                },
                actions = {
                    if (state.messages.isNotEmpty()) {
                        IconButton(onClick = actions.onStartOver, enabled = !state.pending) {
                            Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.feature_assistant_start_over))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { QuestionBox(state, actions) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = list,
                modifier = Modifier.readableWidth().fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "notice") { Notice(actions.onOpenPrivacy) }
                if (state.messages.isEmpty() && state.available) {
                    item(key = "starters") { Starters(enabled = !state.pending, onAsk = actions.onAsk) }
                }
                items(state.messages, key = { it.id }) { message ->
                    if (message.fromUser) Question(message.text) else Answer(message, state.signedIn, actions)
                }
                if (state.pending) item(key = "thinking") { Thinking() }
                state.error?.let { error -> item(key = "error") { Failure(error, state, actions) } }
            }
        }
    }
}

@Composable
private fun Notice(onOpenPrivacy: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.feature_assistant_notice), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onOpenPrivacy, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(stringResource(R.string.feature_assistant_notice_link))
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 6.dp).size(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Starters(
    enabled: Boolean,
    onAsk: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.feature_assistant_welcome),
            style = MaterialTheme.typography.bodyLarge,
            color = BookStoreTheme.colors.textMuted,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            STARTERS.forEach { starter ->
                val question = stringResource(starter)
                SuggestionChip(onClick = { onAsk(question) }, enabled = enabled, label = { Text(question) })
            }
        }
    }
}

@Composable
private fun Question(text: String) {
    val spoken = stringResource(R.string.feature_assistant_you, text)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp),
            modifier = Modifier.widthIn(max = BUBBLE_MAX_DP.dp).clearAndSetSemantics { contentDescription = spoken },
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

/** An answer: its plain text, then the books, orders, pages and people the shop found for it. */
@Composable
private fun Answer(
    message: AssistantMessage,
    signedIn: Boolean,
    actions: AssistantActions,
) {
    val reply = message.reply
    val spoken = stringResource(R.string.feature_assistant_answer, message.text)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp),
            modifier =
                Modifier.semantics {
                    // A new answer is read out as it arrives.
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = spoken
                },
        ) {
            // Plain text, exactly as the server sent it: never read for links or markup.
            Text(
                message.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        if (reply != null) {
            if (reply.books.isNotEmpty()) Books(reply.books.take(MAX_BOOKS), actions.onOpenBook)
            reply.orders.forEach { order -> OrderCard(order, actions.onOpenLink) }
            if (reply.links.isNotEmpty()) Links(reply.links, actions.onOpenLink)
            reply.handover?.let { handover -> Handover(handover, signedIn, actions) }
            if (reply.wanted != null) Wanted(message, reply, signedIn, actions)
            if (reply.requestId.isNotEmpty()) Feedback(message, actions.onFeedback)
        }
    }
}

@Composable
private fun Books(
    books: List<Book>,
    onOpenBook: (Book) -> Unit,
) {
    val label = stringResource(R.string.feature_assistant_books)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        items(books, key = { it.id }) { book -> BookCard(book = book, onClick = { onOpenBook(book) }, width = BOOK_CARD_DP.dp) }
    }
}

@Composable
private fun OrderCard(
    order: AssistantOrder,
    onOpenLink: (String) -> Unit,
) {
    val locale = currentLocale()
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.feature_assistant_order_number, order.orderNumber),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            val placed = formatDate(order.placedAt)
            if (placed.isNotEmpty()) {
                Text(
                    stringResource(R.string.feature_assistant_order_placed, placed),
                    style = MaterialTheme.typography.bodySmall,
                    color = BookStoreTheme.colors.textMuted,
                )
            }
            Text(
                order.statusLabel,
                style = MaterialTheme.typography.titleSmall,
                color = if (order.cancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            if (!order.cancelled) {
                val steps = OrderStatus.steps.size
                val step = (order.stage + 1).coerceIn(1, steps)
                val spoken = stringResource(R.string.feature_assistant_order_step, formatCount(step, locale), formatCount(steps, locale))
                LinearProgressIndicator(
                    progress = { step.toFloat() / steps },
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
                )
            }
            if (order.explanation.isNotBlank()) Text(order.explanation, style = MaterialTheme.typography.bodyMedium)
            order.books.forEach { book ->
                Text(
                    stringResource(R.string.feature_assistant_order_line, book.title, formatCount(book.quantity, locale)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                stringResource(R.string.feature_assistant_order_total, formatTaka(order.total, locale)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (order.canCancel) {
                Text(stringResource(R.string.feature_assistant_order_can_cancel), style = MaterialTheme.typography.bodySmall)
            }
            val returnable = formatDate(order.returnableUntil)
            if (returnable.isNotEmpty()) {
                Text(stringResource(R.string.feature_assistant_order_returnable, returnable), style = MaterialTheme.typography.bodySmall)
            }
            if (order.link.startsWith("/") && !order.link.startsWith("//")) {
                OutlinedButton(onClick = { onOpenLink(order.link) }) { Text(stringResource(R.string.feature_assistant_order_open)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Links(
    links: List<AssistantLink>,
    onOpenLink: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        links.forEach { link -> AssistChip(onClick = { onOpenLink(link.href) }, label = { Text(link.label) }) }
    }
}

/** A person to talk to. Nothing is sent from here: the buttons only open a chat or an e-mail. */
@Composable
private fun Handover(
    handover: AssistantHandover,
    signedIn: Boolean,
    actions: AssistantActions,
) {
    var summary by rememberSaveable { mutableStateOf(false) }
    val chatName = if (handover.toSeller) handover.name else stringResource(R.string.feature_assistant_shop_name)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (handover.toSeller) {
                    stringResource(R.string.feature_assistant_handover_seller, handover.name)
                } else {
                    stringResource(R.string.feature_assistant_handover_shop)
                },
                style = MaterialTheme.typography.titleSmall,
            )
            if (handover.chatWith != null) {
                if (signedIn) {
                    if (handover.toSeller) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(value = summary, role = Role.Checkbox, onValueChange = { summary = it }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = summary, onCheckedChange = null)
                            Text(
                                stringResource(R.string.feature_assistant_handover_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                    Button(onClick = { actions.onChat(handover, summary) }) {
                        Text(stringResource(R.string.feature_assistant_handover_chat, chatName))
                    }
                } else {
                    Button(onClick = actions.onSignIn) { Text(stringResource(R.string.feature_assistant_handover_sign_in)) }
                }
            }
            val email = handover.email
            if (!handover.toSeller && email != null) {
                OutlinedButton(onClick = { actions.onEmail(email) }) { Text(stringResource(R.string.feature_assistant_handover_email)) }
            }
        }
    }
}

/** A book the shop does not have. It is asked for on the Wanted board only when the person presses the button. */
@Composable
private fun Wanted(
    message: AssistantMessage,
    reply: AssistantReply,
    signedIn: Boolean,
    actions: AssistantActions,
) {
    val offer = reply.wanted ?: return
    when {
        message.wantedMessage != null -> {
            Text(
                message.wantedMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = BookStoreTheme.colors.success,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        signedIn -> {
            OutlinedButton(onClick = { actions.onAskWanted(message.id) }, enabled = !message.askingWanted) {
                Text(stringResource(R.string.feature_assistant_wanted_ask, offer.title))
            }
        }

        else -> {
            OutlinedButton(onClick = actions.onSignIn) { Text(stringResource(R.string.feature_assistant_wanted_sign_in)) }
        }
    }
}

@Composable
private fun Feedback(
    message: AssistantMessage,
    onFeedback: (Int, Boolean) -> Unit,
) {
    if (message.feedbackSent) {
        Text(
            stringResource(R.string.feature_assistant_feedback_thanks),
            style = MaterialTheme.typography.bodySmall,
            color = BookStoreTheme.colors.textMuted,
            modifier = Modifier.padding(vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
    } else {
        Row {
            IconButton(onClick = { onFeedback(message.id, true) }) {
                Icon(Icons.Outlined.ThumbUp, contentDescription = stringResource(R.string.feature_assistant_helpful))
            }
            IconButton(onClick = { onFeedback(message.id, false) }) {
                Icon(Icons.Outlined.ThumbDown, contentDescription = stringResource(R.string.feature_assistant_not_helpful))
            }
        }
    }
}

@Composable
private fun Thinking() {
    Row(
        Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(stringResource(R.string.feature_assistant_thinking), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Why the last question got no answer, in the server's words where it gave any. The question waits in the box. */
@Composable
private fun Failure(
    error: AppError,
    state: AssistantUiState,
    actions: AssistantActions,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (state.available) {
                    errorMessage(error)
                } else {
                    (error as? AppError.Rejected)?.message ?: stringResource(R.string.feature_assistant_off)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.available) {
                Text(stringResource(R.string.feature_assistant_error_keeps_question), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // A visitor shares the daily questions with everyone on the same network; an account has its own.
                if (error is AppError.RateLimited && !state.signedIn) {
                    TextButton(onClick = actions.onSignIn) { Text(stringResource(R.string.feature_assistant_sign_in_for_more)) }
                }
                TextButton(onClick = actions.onDismissError) { Text(stringResource(R.string.feature_assistant_dismiss)) }
            }
        }
    }
}

@Composable
private fun QuestionBox(
    state: AssistantUiState,
    actions: AssistantActions,
) {
    val locale = currentLocale()
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = actions.onInput,
                enabled = state.available,
                placeholder = { Text(stringResource(R.string.feature_assistant_input)) },
                supportingText =
                    if (state.input.length >= COUNTER_FROM) {
                        {
                            Text(
                                stringResource(
                                    R.string.feature_assistant_counter,
                                    formatCount(state.input.length, locale),
                                    formatCount(MAX_QUESTION, locale),
                                ),
                            )
                        }
                    } else {
                        null
                    },
                shape = RoundedCornerShape(24.dp),
                maxLines = QUESTION_LINES,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            )
            IconButton(onClick = actions.onSend, enabled = state.canSend) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.feature_assistant_send))
            }
        }
    }
}

private val STARTERS =
    listOf(
        R.string.feature_assistant_starter_delivery,
        R.string.feature_assistant_starter_returns,
        R.string.feature_assistant_starter_track,
        R.string.feature_assistant_starter_find,
    )

private const val BUBBLE_MAX_DP = 320
private const val BOOK_CARD_DP = 148
private const val MAX_BOOKS = 6
private const val QUESTION_LINES = 5
private const val MAX_QUESTION = 500
private const val COUNTER_FROM = 400
