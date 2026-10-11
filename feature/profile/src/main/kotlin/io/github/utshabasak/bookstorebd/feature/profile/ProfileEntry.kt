package io.github.utshabasak.bookstorebd.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.SetupItem
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.navigation.EditProfileRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.HomeRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.InviteRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.PrivacyRoute
import java.time.LocalDate

/** Registers editing the profile, privacy and data, and inviting friends. */
fun EntryProviderScope<NavKey>.profileEntries(
    navigator: Navigator,
    openPage: (String) -> Unit,
) {
    entry<EditProfileRoute> { route -> EditProfileDestination(route, navigator) }

    entry<PrivacyRoute> { PrivacyDestination(navigator, openPage) }

    entry<InviteRoute> { InviteDestination(navigator) }
}

@Composable
private fun EditProfileDestination(
    route: EditProfileRoute,
    navigator: Navigator,
) {
    val viewModel: EditProfileViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var failure by remember { mutableStateOf<AppError?>(null) }
    var slot by rememberSaveable { mutableStateOf(PictureSlot.Photo) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) viewModel.choosePicture(slot, uri)
        }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                EditProfileEvent.Saved -> navigator.back()
                is EditProfileEvent.Failed -> failure = event.error
            }
        }
    }
    ErrorSnackbarEffect(failure, snackbar) { failure = null }
    EditProfileScreen(
        state = state,
        focus = route.focus?.let { name -> SetupItem.entries.firstOrNull { it.name == name } },
        snackbar = snackbar,
        actions =
            EditProfileActions(
                onBack = navigator::back,
                onEdit = viewModel::edit,
                onPickPicture = { chosen ->
                    slot = chosen
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemovePicture = viewModel::removePicture,
                onSave = viewModel::save,
            ),
    )
}

@Composable
private fun PrivacyDestination(
    navigator: Navigator,
    openPage: (String) -> Unit,
) {
    val viewModel: PrivacyViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var failure by remember { mutableStateOf<AppError?>(null) }
    val exported = stringResource(R.string.feature_profile_downloaded)
    val saveFile =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) viewModel.export(uri)
        }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                PrivacyEvent.Exported -> snackbar.showSnackbar(exported)
                PrivacyEvent.Deleted -> navigator.replace(HomeRoute)
                is PrivacyEvent.Failed -> failure = event.error
            }
        }
    }
    ErrorSnackbarEffect(failure, snackbar) { failure = null }
    PrivacyScreen(
        state = state,
        snackbar = snackbar,
        actions =
            PrivacyActions(
                onBack = navigator::back,
                onRememberSearches = viewModel::setRememberSearches,
                onRememberViewed = viewModel::setRememberViewed,
                onExport = { saveFile.launch("bookstorebd-export-${LocalDate.now()}.json") },
                onDelete = viewModel::delete,
                onDismissDeleteProblem = viewModel::clearDeleteProblem,
                onOpenPolicy = { state.policyUrl?.let(openPage) },
            ),
    )
}

@Composable
private fun InviteDestination(
    navigator: Navigator,
) {
    val viewModel: InviteViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var failure by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is InviteEvent.Sent -> snackbar.showSnackbar(event.message)
                is InviteEvent.Failed -> failure = event.error
            }
        }
    }
    ErrorSnackbarEffect(failure, snackbar) { failure = null }
    InviteScreen(
        state = state,
        snackbar = snackbar,
        actions =
            InviteActions(
                onBack = navigator::back,
                onEmails = viewModel::setEmails,
                onNote = viewModel::setNote,
                onSend = viewModel::send,
                onRetry = viewModel::load,
            ),
    )
}
