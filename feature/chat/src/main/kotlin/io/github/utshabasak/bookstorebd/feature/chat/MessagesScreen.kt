package io.github.utshabasak.bookstorebd.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.Conversation
import io.github.utshabasak.bookstorebd.core.ui.BookRowSkeleton
import io.github.utshabasak.bookstorebd.core.ui.EmptyState
import io.github.utshabasak.bookstorebd.core.ui.ErrorState
import io.github.utshabasak.bookstorebd.core.ui.NotificationsPermissionCard
import io.github.utshabasak.bookstorebd.core.ui.SkeletonScreen
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatDate
import io.github.utshabasak.bookstorebd.core.ui.readableWidth

data class MessagesActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onOpen: (Conversation) -> Unit,
    val onSignIn: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    state: MessagesUiState,
    actions: MessagesActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_chat_messages)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_chat_back))
                    }
                },
            )
        },
    ) { padding ->
        val inner = Modifier.fillMaxSize().padding(padding)
        val list = state.conversations
        when {
            state.signedIn == false -> {
                EmptyState(
                    icon = Icons.Outlined.Lock,
                    title = stringResource(R.string.feature_chat_signed_out),
                    body = stringResource(R.string.feature_chat_signed_out_body),
                    actionLabel = stringResource(R.string.feature_chat_sign_in),
                    onAction = actions.onSignIn,
                    modifier = inner,
                )
            }

            list == null && state.error != null -> {
                ErrorState(state.error, onRetry = actions.onRefresh, modifier = inner)
            }

            list == null -> {
                SkeletonScreen(inner) { Column { repeat(PLACEHOLDERS) { BookRowSkeleton() } } }
            }

            else -> {
                PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = actions.onRefresh, modifier = inner) {
                    LazyColumn(Modifier.fillMaxSize().readableWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
                        item { NotificationsPermissionCard(Modifier.padding(16.dp)) }
                        if (list.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = Icons.AutoMirrored.Outlined.Chat,
                                    title = stringResource(R.string.feature_chat_empty),
                                    body = stringResource(R.string.feature_chat_empty_body),
                                )
                            }
                        }
                        items(list, key = { it.email }) { conversation ->
                            ConversationRow(conversation) { actions.onOpen(conversation) }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
) {
    val unread = conversation.unread > 0
    val locale = currentLocale()
    val unreadText =
        if (unread) {
            pluralStringResource(
                R.plurals.feature_chat_unread,
                conversation.unread,
                formatCount(conversation.unread, locale),
            )
        } else {
            null
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(conversation.name, conversation.avatarUrl)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatDate(conversation.lastMessageAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = BookStoreTheme.colors.textMuted,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.lastMessage.ifBlank { stringResource(R.string.feature_chat_photo) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (unread) MaterialTheme.colorScheme.onSurface else BookStoreTheme.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unreadText != null) {
                    Badge(Modifier.semantics { contentDescription = unreadText }) {
                        Text(formatCount(conversation.unread, locale))
                    }
                }
            }
        }
    }
}

@Composable
internal fun Avatar(
    name: String,
    url: String?,
    size: Int = AVATAR_DP,
) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

private const val PLACEHOLDERS = 6
private const val AVATAR_DP = 48
