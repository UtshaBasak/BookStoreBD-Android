package io.github.utshabasak.bookstorebd.feature.assistant

import android.content.Context
import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.navigation.AssistantRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.ChatRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignInRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.routeForSiteLink
import io.github.utshabasak.bookstorebd.core.ui.openInBrowser

/** Registers the shopping assistant. */
fun EntryProviderScope<NavKey>.assistantEntry(navigator: Navigator) {
    entry<AssistantRoute> { route -> AssistantDestination(route, navigator) }
}

@Composable
private fun AssistantDestination(
    route: AssistantRoute,
    navigator: Navigator,
) {
    val viewModel = hiltViewModel<AssistantViewModel, AssistantViewModel.Factory>(creationCallback = { it.create(route) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var failure by remember { mutableStateOf<AppError?>(null) }
    val shopName = stringResource(R.string.feature_assistant_shop_name)
    val summaryPlain = stringResource(R.string.feature_assistant_summary_plain)
    val summaryAbout = stringResource(R.string.feature_assistant_summary_about, "%s")
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is AssistantEvent.Failed -> failure = event.error
            }
        }
    }
    ErrorSnackbarEffect(failure, snackbar) { failure = null }
    AssistantScreen(
        state = state,
        snackbar = snackbar,
        actions =
            AssistantActions(
                onBack = navigator::back,
                onInput = viewModel::setInput,
                onSend = viewModel::send,
                onAsk = viewModel::ask,
                onStartOver = viewModel::startOver,
                onDismissError = viewModel::dismissError,
                onOpenPrivacy = { if (state.privacy.isNotBlank()) openInBrowser(context, state.privacy.substringBefore('#') + "#assistant") },
                onOpenBook = { navigator.navigate(BookRoute(it.id)) },
                onOpenLink = { path -> openSitePath(context, navigator, state.site, path) },
                onChat = { handover, withSummary ->
                    val email = handover.chatWith
                    if (email != null) {
                        val heading = handover.bookTitle?.let { summaryAbout.replace("%s", it) } ?: summaryPlain
                        val draft = if (withSummary) summaryDraft(heading, state.lastQuestions()) else ""
                        navigator.navigate(ChatRoute(email, if (handover.toSeller) handover.name else shopName, draft))
                    }
                },
                onEmail = { address -> writeTo(context, address) },
                onSignIn = { navigator.navigate(SignInRoute) },
                onFeedback = viewModel::feedback,
                onAskWanted = viewModel::askOnWantedBoard,
            ),
    )
}

/**
 * Opens a path on the shop's site: the app's own screen where it has one,
 * otherwise the website's page. Anything that is not a path on the site is
 * ignored.
 */
internal fun openSitePath(
    context: Context,
    navigator: Navigator,
    site: String,
    path: String,
) {
    if (!path.startsWith("/") || path.startsWith("//")) return
    val screen = routeForSiteLink(path)
    when {
        screen != null -> navigator.navigate(screen)
        site.isNotBlank() -> openInBrowser(context, site.trimEnd('/') + path)
    }
}

/** What a chat with a seller starts with when the person asks for a summary: their own last questions. */
internal fun summaryDraft(
    heading: String,
    questions: List<String>,
): String = (listOf(heading) + questions.map { "- $it" }).joinToString("\n")

/** Opens the phone's e-mail app with a message to [address]; nothing is sent from here. */
private fun writeTo(
    context: Context,
    address: String,
) {
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:$address".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
