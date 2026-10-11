package io.github.utshabasak.bookstorebd.feature.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.navigation.ChatRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.MessagesRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.ShopRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignInRoute

/** Registers the conversations and a conversation. */
fun EntryProviderScope<NavKey>.chatEntries(navigator: Navigator) {
    entry<MessagesRoute> { MessagesDestination(navigator) }
    entry<ChatRoute> { route -> ChatDestination(route, navigator) }
}

@Composable
private fun MessagesDestination(navigator: Navigator) {
    val viewModel: MessagesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Fresh each time the list is shown, as after reading a conversation.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    MessagesScreen(
        state = state,
        actions =
            MessagesActions(
                onBack = navigator::back,
                onRefresh = viewModel::refresh,
                onOpen = { navigator.navigate(ChatRoute(it.email, it.name)) },
                onSignIn = { navigator.navigate(SignInRoute) },
            ),
    )
}

@Composable
private fun ChatDestination(
    route: ChatRoute,
    navigator: Navigator,
) {
    val viewModel = hiltViewModel<ChatViewModel, ChatViewModel.Factory>(creationCallback = { it.create(route) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var failure by remember { mutableStateOf<AppError?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::sendPhoto) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreen(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onScreen(false) }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ChatEvent.Failed -> failure = event.error
                ChatEvent.Deleted -> navigator.back()
            }
        }
    }
    ErrorSnackbarEffect(failure, snackbar) { failure = null }
    ChatScreen(
        name = name.ifBlank { route.email.substringBefore('@') },
        state = state,
        snackbar = snackbar,
        actions =
            ChatActions(
                onBack = navigator::back,
                onRetryLoad = viewModel::load,
                onLoadOlder = viewModel::loadOlder,
                onDraft = viewModel::setDraft,
                onSend = viewModel::sendText,
                onPickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRetry = viewModel::retry,
                onDelete = viewModel::delete,
                onOpenShop = { if (name.isNotBlank()) navigator.navigate(ShopRoute(name)) },
            ),
    )
}
