package io.github.utshabasak.bookstorebd.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.ProfileRepository
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Invites
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.ui.ErrorState
import io.github.utshabasak.bookstorebd.core.ui.formatDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InviteUiState(
    val invites: Invites? = null,
    val loadError: AppError? = null,
    val emails: String = "",
    val note: String = "",
    val sending: Boolean = false,
    val problem: InviteProblem? = null,
) {
    /** The addresses typed, split on commas, spaces and new lines. */
    val parsed: List<String> get() = emails.split(',', ' ', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

enum class InviteProblem { NoAddress, TooMany, NotAnAddress, LinkInNote }

sealed interface InviteEvent {
    data class Sent(
        val message: String,
    ) : InviteEvent

    data class Failed(
        val error: AppError,
    ) : InviteEvent
}

/** Inviting friends by e-mail: up to five at a time, with a short note, as on the website. */
@HiltViewModel
class InviteViewModel
    @Inject
    constructor(
        private val profiles: ProfileRepository,
    ) : ViewModel() {
        private val state = MutableStateFlow(InviteUiState())
        val uiState: StateFlow<InviteUiState> = state.asStateFlow()

        private val events = Channel<InviteEvent>(Channel.BUFFERED)
        val eventFlow: Flow<InviteEvent> = events.receiveAsFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                when (val result = profiles.invites()) {
                    is Outcome.Success -> state.update { it.copy(invites = result.value, loadError = null) }
                    is Outcome.Failure -> state.update { it.copy(loadError = result.error) }
                }
            }
        }

        fun setEmails(text: String) = state.update { it.copy(emails = text, problem = null) }

        fun setNote(text: String) = state.update { it.copy(note = text.take(NOTE_LIMIT), problem = null) }

        fun send() {
            val current = state.value
            val addresses = current.parsed
            val problem =
                when {
                    addresses.isEmpty() -> InviteProblem.NoAddress
                    addresses.size > MAX_AT_ONCE -> InviteProblem.TooMany
                    addresses.any { !EMAIL.matches(it) } -> InviteProblem.NotAnAddress
                    LINK.containsMatchIn(current.note) -> InviteProblem.LinkInNote
                    else -> null
                }
            if (problem != null) {
                state.update { it.copy(problem = problem) }
                return
            }
            viewModelScope.launch {
                state.update { it.copy(sending = true) }
                when (val result = profiles.invite(addresses, current.note)) {
                    is Outcome.Success -> {
                        state.update { it.copy(sending = false, emails = "", note = "") }
                        events.send(InviteEvent.Sent(result.value))
                        load()
                    }

                    is Outcome.Failure -> {
                        state.update { it.copy(sending = false) }
                        events.send(InviteEvent.Failed(result.error))
                    }
                }
            }
        }

        private companion object {
            const val MAX_AT_ONCE = 5
            const val NOTE_LIMIT = 300
            val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
            val LINK = Regex("(https?://|www\\.|\\.(com|net|org|bd)\\b)", RegexOption.IGNORE_CASE)
        }
    }

data class InviteActions(
    val onBack: () -> Unit,
    val onEmails: (String) -> Unit,
    val onNote: (String) -> Unit,
    val onSend: () -> Unit,
    val onRetry: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteScreen(
    state: InviteUiState,
    actions: InviteActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_profile_invite_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_profile_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val invites = state.invites
        if (invites == null && state.loadError != null) {
            ErrorState(state.loadError, onRetry = actions.onRetry, modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(16.dp)) {
            item { InviteForm(state, invites, actions) }
            if (!invites?.invited.isNullOrEmpty()) {
                item {
                    Text(
                        stringResource(R.string.feature_profile_invited),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp).semantics { heading() },
                    )
                }
                items(invites.invited, key = { it.email }) { invitee ->
                    ListItem(
                        headlineContent = { Text(invitee.email) },
                        leadingContent = { Icon(Icons.Outlined.MailOutline, contentDescription = null) },
                        trailingContent = {
                            if (invitee.joined) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text(stringResource(R.string.feature_profile_joined)) },
                                    leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null, Modifier.size(18.dp)) },
                                    colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = BookStoreTheme.colors.success),
                                )
                            } else {
                                Text(
                                    stringResource(R.string.feature_profile_invited_on, formatDate(invitee.invitedAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun InviteForm(
    state: InviteUiState,
    invites: Invites?,
    actions: InviteActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.feature_profile_invite_body), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = state.emails,
            onValueChange = actions.onEmails,
            label = { Text(stringResource(R.string.feature_profile_invite_emails)) },
            supportingText = {
                Text(
                    when (state.problem) {
                        InviteProblem.NoAddress -> stringResource(R.string.feature_profile_invite_no_address)
                        InviteProblem.TooMany -> stringResource(R.string.feature_profile_invite_too_many)
                        InviteProblem.NotAnAddress -> stringResource(R.string.feature_profile_invite_not_address)
                        else -> stringResource(R.string.feature_profile_invite_emails_help)
                    },
                )
            },
            isError = state.problem in setOf(InviteProblem.NoAddress, InviteProblem.TooMany, InviteProblem.NotAnAddress),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.note,
            onValueChange = actions.onNote,
            label = { Text(stringResource(R.string.feature_profile_invite_note)) },
            supportingText = {
                Text(
                    if (state.problem == InviteProblem.LinkInNote) {
                        stringResource(R.string.feature_profile_invite_no_links)
                    } else {
                        stringResource(R.string.feature_profile_invite_note_count, state.note.length)
                    },
                )
            },
            isError = state.problem == InviteProblem.LinkInNote,
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        val remaining = invites?.remainingToday
        if (remaining != null) {
            Text(
                pluralStringResource(R.plurals.feature_profile_invites_left, remaining, remaining),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = actions.onSend,
            enabled = !state.sending && (remaining == null || remaining > 0),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            if (state.sending) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.feature_profile_invite_send))
            }
        }
    }
}
