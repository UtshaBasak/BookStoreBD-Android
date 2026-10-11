package io.github.utshabasak.bookstorebd.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.ui.EmptyState
import io.github.utshabasak.bookstorebd.core.ui.ErrorState
import io.github.utshabasak.bookstorebd.core.ui.LocalPrivateImageLoader
import io.github.utshabasak.bookstorebd.core.ui.formatDate

data class ChatActions(
    val onBack: () -> Unit,
    val onRetryLoad: () -> Unit,
    val onLoadOlder: () -> Unit,
    val onDraft: (String) -> Unit,
    val onSend: () -> Unit,
    val onPickPhoto: () -> Unit,
    val onRetry: (Bubble) -> Unit,
    val onDelete: () -> Unit,
    val onOpenShop: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    name: String,
    state: ChatUiState,
    actions: ChatActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(name, modifier = Modifier.clickable(onClick = actions.onOpenShop)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_chat_back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.feature_chat_more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.feature_chat_delete)) },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { Composer(state.draft, actions) },
    ) { padding ->
        val inner = Modifier.fillMaxSize().padding(padding)
        when {
            state.loading && state.bubbles.isEmpty() -> {
                Box(inner, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            state.error != null && state.bubbles.isEmpty() -> {
                ErrorState(state.error, onRetry = actions.onRetryLoad, modifier = inner)
            }

            state.bubbles.isEmpty() -> {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.Chat,
                    title = stringResource(R.string.feature_chat_start, name),
                    body = stringResource(R.string.feature_chat_start_body),
                    modifier = inner,
                )
            }

            else -> {
                Thread(state, actions, inner)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.feature_chat_delete_title)) },
            text = { Text(stringResource(R.string.feature_chat_delete_body, name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.onDelete()
                }) { Text(stringResource(R.string.feature_chat_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.feature_chat_keep)) } },
        )
    }
}

@Composable
private fun Thread(
    state: ChatUiState,
    actions: ChatActions,
    modifier: Modifier,
) {
    val list = rememberLazyListState()
    // Newest at the bottom: the list is drawn from the end, so a new message is in view.
    val newestFirst = state.bubbles.asReversed()
    LaunchedEffect(state.bubbles.lastOrNull()?.message?.id) { list.animateScrollToItem(0) }
    LaunchedEffect(list.canScrollForward, state.hasOlder) { if (!list.canScrollForward && state.hasOlder) actions.onLoadOlder() }
    LazyColumn(
        state = list,
        reverseLayout = true,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(newestFirst, key = { it.message.id }) { bubble -> BubbleRow(bubble, actions.onRetry) }
        if (state.loadingOlder) {
            item {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun BubbleRow(
    bubble: Bubble,
    onRetry: (Bubble) -> Unit,
) {
    val message = bubble.message
    val shape = RoundedCornerShape(16.dp, 16.dp, if (bubble.mine) 4.dp else 16.dp, if (bubble.mine) 16.dp else 4.dp)
    val failedLabel = stringResource(R.string.feature_chat_not_sent)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (bubble.mine) Alignment.End else Alignment.Start) {
        Surface(
            shape = shape,
            color = if (bubble.mine) BookStoreTheme.colors.brandFill else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (bubble.mine) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .widthIn(max = BUBBLE_MAX_DP.dp)
                    .clickable(enabled = bubble.failed) { onRetry(bubble) }
                    .semantics { if (bubble.failed) contentDescription = failedLabel },
        ) {
            Column(Modifier.padding(PHOTO_PAD.dp)) {
                val photo: Any? = bubble.localPhoto ?: message.imageUrl
                if (photo != null) {
                    AsyncImage(
                        model = photo,
                        imageLoader = privateLoader(),
                        contentDescription = stringResource(R.string.feature_chat_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(PHOTO_DP.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
                if (message.text.isNotBlank()) {
                    Text(
                        message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                bubble.failed -> {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        stringResource(R.string.feature_chat_tap_to_retry),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                bubble.pending -> {
                    Text(
                        stringResource(R.string.feature_chat_sending),
                        style = MaterialTheme.typography.labelSmall,
                        color = BookStoreTheme.colors.textMuted,
                    )
                }

                else -> {
                    Text(
                        formatDate(message.sentAt, withTime = true),
                        style = MaterialTheme.typography.labelSmall,
                        color = BookStoreTheme.colors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun privateLoader(): coil3.ImageLoader =
    LocalPrivateImageLoader.current ?: coil3.compose.LocalPlatformContext.current.let { coil3.SingletonImageLoader.get(it) }

@Composable
private fun Composer(
    draft: String,
    actions: ChatActions,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(
                WindowInsets.navigationBars.union(WindowInsets.ime),
            ).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions.onPickPhoto) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = stringResource(R.string.feature_chat_attach))
            }
            OutlinedTextField(
                value = draft,
                onValueChange = actions.onDraft,
                placeholder = { Text(stringResource(R.string.feature_chat_write)) },
                shape = RoundedCornerShape(24.dp),
                maxLines = COMPOSER_LINES,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            )
            IconButton(onClick = actions.onSend, enabled = draft.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.feature_chat_send))
            }
        }
    }
}

private const val BUBBLE_MAX_DP = 300
private const val PHOTO_DP = 220
private const val PHOTO_PAD = 4
private const val COMPOSER_LINES = 5
