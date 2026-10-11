package io.github.utshabasak.bookstorebd.feature.profile

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.CatalogueRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ProfileRepository
import io.github.utshabasak.bookstorebd.core.data.repository.SettingsRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.ui.readableWidth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrivacyUiState(
    val rememberSearches: Boolean = true,
    val rememberViewed: Boolean = true,
    val signedIn: Boolean = false,
    val policyUrl: String? = null,
    val exporting: Boolean = false,
    val deleting: Boolean = false,
    /** Set when the password given for deleting the account was not right. */
    val deleteProblem: String? = null,
)

sealed interface PrivacyEvent {
    data object Exported : PrivacyEvent

    data object Deleted : PrivacyEvent

    data class Failed(
        val error: AppError,
    ) : PrivacyEvent
}

@HiltViewModel
class PrivacyViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val catalogue: CatalogueRepository,
        private val profiles: ProfileRepository,
        session: UserSession,
        config: ConfigRepository,
    ) : ViewModel() {
        private val busy = MutableStateFlow(PrivacyUiState())

        val uiState: StateFlow<PrivacyUiState> =
            combine(settings.rememberSearches, settings.rememberViewed, session.user, config.config, busy) {
                searches,
                viewed,
                user,
                conf,
                current,
                ->
                current.copy(
                    rememberSearches = searches,
                    rememberViewed = viewed,
                    signedIn = user != null,
                    policyUrl = conf?.links?.privacy,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), PrivacyUiState())

        private val events = Channel<PrivacyEvent>(Channel.BUFFERED)
        val eventFlow: Flow<PrivacyEvent> = events.receiveAsFlow()

        /** Turning a choice off also forgets what was kept, as the website does. */
        fun setRememberSearches(remember: Boolean) {
            viewModelScope.launch {
                settings.setRememberSearches(remember)
                if (!remember) catalogue.clearRecentSearches()
            }
        }

        fun setRememberViewed(remember: Boolean) {
            viewModelScope.launch {
                settings.setRememberViewed(remember)
                if (!remember) catalogue.clearRecentlyViewed()
            }
        }

        fun export(destination: Uri) {
            viewModelScope.launch {
                busy.value = busy.value.copy(exporting = true)
                val result = profiles.exportData(destination)
                busy.value = busy.value.copy(exporting = false)
                events.send(if (result is Outcome.Failure) PrivacyEvent.Failed(result.error) else PrivacyEvent.Exported)
            }
        }

        fun delete(password: String) {
            viewModelScope.launch {
                busy.value = busy.value.copy(deleting = true, deleteProblem = null)
                when (val result = profiles.deleteAccount(password)) {
                    is Outcome.Success -> {
                        busy.value = busy.value.copy(deleting = false)
                        events.send(PrivacyEvent.Deleted)
                    }

                    is Outcome.Failure -> {
                        val rejected = result.error as? AppError.Rejected
                        busy.value =
                            busy.value.copy(
                                deleting = false,
                                deleteProblem =
                                    rejected?.message.takeIf {
                                        rejected?.status == HTTP_FORBIDDEN
                                    },
                            )
                        if (rejected?.status != HTTP_FORBIDDEN) events.send(PrivacyEvent.Failed(result.error))
                    }
                }
            }
        }

        fun clearDeleteProblem() {
            busy.value = busy.value.copy(deleteProblem = null)
        }

        private companion object {
            const val STOP_MS = 5_000L
            const val HTTP_FORBIDDEN = 403
        }
    }

data class PrivacyActions(
    val onBack: () -> Unit,
    val onRememberSearches: (Boolean) -> Unit,
    val onRememberViewed: (Boolean) -> Unit,
    val onExport: () -> Unit,
    val onDelete: (String) -> Unit,
    val onDismissDeleteProblem: () -> Unit,
    val onOpenPolicy: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    state: PrivacyUiState,
    actions: PrivacyActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_profile_privacy_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_profile_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).readableWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Heading(R.string.feature_profile_on_this_phone) }
            item {
                Text(
                    stringResource(R.string.feature_profile_on_this_phone_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                SwitchRow(
                    R.string.feature_profile_remember_searches,
                    R.string.feature_profile_remember_searches_body,
                    state.rememberSearches,
                    actions.onRememberSearches,
                )
            }
            item {
                SwitchRow(
                    R.string.feature_profile_remember_viewed,
                    R.string.feature_profile_remember_viewed_body,
                    state.rememberViewed,
                    actions.onRememberViewed,
                )
            }
            item {
                TextButton(onClick = actions.onOpenPolicy, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.feature_profile_read_policy))
                }
            }
            if (state.signedIn) {
                item { Heading(R.string.feature_profile_your_data) }
                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.feature_profile_download_body), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = actions.onExport,
                            enabled = !state.exporting,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            if (state.exporting) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Download, contentDescription = null)
                                Text(stringResource(R.string.feature_profile_download), Modifier.padding(start = 8.dp))
                            }
                        }
                        Text(stringResource(R.string.feature_profile_delete_body), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                            Text(stringResource(R.string.feature_profile_delete), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete || state.deleteProblem != null || state.deleting) {
        DeleteDialog(
            busy = state.deleting,
            problem = state.deleteProblem,
            onDelete = actions.onDelete,
            onDismiss = {
                confirmDelete = false
                actions.onDismissDeleteProblem()
            },
        )
    }
}

@Composable
private fun DeleteDialog(
    busy: Boolean,
    problem: String?,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.feature_profile_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.feature_profile_delete_explained))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.feature_profile_password_label)) },
                    isError = problem != null,
                    supportingText = problem?.let { { Text(it) } },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onDelete(password) },
                enabled = password.isNotEmpty() && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.feature_profile_delete_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.feature_profile_keep_account)) }
        },
    )
}

@Composable
private fun Heading(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun SwitchRow(
    title: Int,
    body: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(body)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}
