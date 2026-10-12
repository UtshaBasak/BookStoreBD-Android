package io.github.utshabasak.bookstorebd.feature.notifications

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppNotification
import io.github.utshabasak.bookstorebd.core.model.NotificationCategory
import io.github.utshabasak.bookstorebd.core.ui.BookRowSkeleton
import io.github.utshabasak.bookstorebd.core.ui.EmptyState
import io.github.utshabasak.bookstorebd.core.ui.ErrorState
import io.github.utshabasak.bookstorebd.core.ui.NotificationsPermissionCard
import io.github.utshabasak.bookstorebd.core.ui.SkeletonScreen
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatDate
import io.github.utshabasak.bookstorebd.core.ui.readableWidth

data class NotificationsActions(
    val onBack: () -> Unit,
    val onOpen: (AppNotification) -> Unit,
    val onMarkAllRead: () -> Unit,
    val onSettings: () -> Unit,
    val onSignIn: () -> Unit,
    val onUnreadOnly: (Boolean) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    signedIn: Boolean?,
    unread: Int,
    items: LazyPagingItems<AppNotification>,
    marks: ReadMarks,
    actions: NotificationsActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    unreadOnly: Boolean = false,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_notifications_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_notifications_back))
                    }
                },
                actions = {
                    if (signedIn == true) {
                        if (unread > 0 && !marks.all) {
                            IconButton(onClick = actions.onMarkAllRead) {
                                Icon(Icons.Outlined.DoneAll, contentDescription = stringResource(R.string.feature_notifications_mark_all))
                            }
                        }
                        IconButton(onClick = actions.onSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.feature_notifications_settings))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (signedIn == false) {
                EmptyState(
                    icon = Icons.Outlined.Lock,
                    title = stringResource(R.string.feature_notifications_signed_out),
                    body = stringResource(R.string.feature_notifications_signed_out_body),
                    actionLabel = stringResource(R.string.feature_notifications_sign_in),
                    onAction = actions.onSignIn,
                )
            } else {
                NotificationList(items, marks, unreadOnly, actions.onOpen) {
                    UnreadBar(unread = if (marks.all) 0 else unread, unreadOnly = unreadOnly, onUnreadOnly = actions.onUnreadOnly)
                }
            }
        }
    }
}

/** How many are unread, and the website's "Unread only" filter. */
@Composable
private fun UnreadBar(
    unread: Int,
    unreadOnly: Boolean,
    onUnreadOnly: (Boolean) -> Unit,
) {
    val locale = currentLocale()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (unread > 0) {
                pluralStringResource(R.plurals.feature_notifications_unread_count, unread, formatCount(unread, locale))
            } else {
                stringResource(R.string.feature_notifications_caught_up)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = BookStoreTheme.colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = unreadOnly,
            onClick = { onUnreadOnly(!unreadOnly) },
            label = { Text(stringResource(R.string.feature_notifications_unread_only)) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationList(
    items: LazyPagingItems<AppNotification>,
    marks: ReadMarks,
    unreadOnly: Boolean,
    onOpen: (AppNotification) -> Unit,
    header: @Composable () -> Unit,
) {
    val refresh = items.loadState.refresh
    when {
        refresh is LoadState.Loading && items.itemCount == 0 -> {
            SkeletonScreen { Column { repeat(PLACEHOLDERS) { BookRowSkeleton() } } }
        }

        refresh is LoadState.Error && items.itemCount == 0 -> {
            ErrorState(refresh.error.appError(), onRetry = items::retry)
        }

        else -> {
            PullToRefreshBox(
                isRefreshing = refresh is LoadState.Loading,
                onRefresh = items::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(Modifier.fillMaxSize().readableWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item { NotificationsPermissionCard(Modifier.padding(16.dp)) }
                    item { header() }
                    if (refresh is LoadState.NotLoading && items.itemCount == 0) {
                        item {
                            val (title, body) =
                                if (unreadOnly) {
                                    R.string.feature_notifications_none_unread to R.string.feature_notifications_none_unread_body
                                } else {
                                    R.string.feature_notifications_empty to R.string.feature_notifications_empty_body
                                }
                            EmptyState(icon = Icons.Outlined.NotificationsNone, title = stringResource(title), body = stringResource(body))
                        }
                    }
                    items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                        items[index]?.let { notification ->
                            NotificationRow(notification, read = marks.isRead(notification), onClick = { onOpen(notification) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: AppNotification,
    read: Boolean,
    onClick: () -> Unit,
) {
    val unreadLabel = stringResource(R.string.feature_notifications_unread)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(
                    if (read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = UNREAD_TINT),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { if (!read) contentDescription = unreadLabel },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                iconFor(notification.category),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                notification.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (read) FontWeight.Normal else FontWeight.Bold,
            )
            if (notification.body.isNotBlank()) {
                Text(
                    notification.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BookStoreTheme.colors.textMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatDate(notification.createdAt, withTime = true),
                style = MaterialTheme.typography.labelSmall,
                color = BookStoreTheme.colors.textMuted,
            )
        }
        if (!read) {
            Box(Modifier.padding(top = 6.dp).size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

internal fun iconFor(category: NotificationCategory): ImageVector =
    when (category) {
        NotificationCategory.Orders -> Icons.Outlined.LocalShipping
        NotificationCategory.Returns -> Icons.Outlined.Replay
        NotificationCategory.Payouts -> Icons.Outlined.Payments
        NotificationCategory.Reviews -> Icons.Outlined.StarOutline
        NotificationCategory.Stock -> Icons.Outlined.Inventory2
        NotificationCategory.Deals -> Icons.Outlined.LocalOffer
        NotificationCategory.Wanted -> Icons.AutoMirrored.Outlined.ManageSearch
        NotificationCategory.Community -> Icons.Outlined.People
        NotificationCategory.Announcements -> Icons.Outlined.Campaign
        NotificationCategory.Moderation -> Icons.Outlined.Flag
        NotificationCategory.Account -> Icons.Outlined.Shield
    }

private const val PLACEHOLDERS = 6
private const val UNREAD_TINT = 0.35f
