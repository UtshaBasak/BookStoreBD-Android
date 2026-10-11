package io.github.utshabasak.bookstorebd.feature.reviews

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.WriteReviewRoute

/** Registers the review form. */
fun EntryProviderScope<NavKey>.reviewsEntries(navigator: Navigator) {
    entry<WriteReviewRoute> { route ->
        val viewModel = hiltViewModel<WriteReviewViewModel, WriteReviewViewModel.Factory>(creationCallback = { it.create(route) })
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        var failure by remember { mutableStateOf<AppError?>(null) }
        LaunchedEffect(viewModel) {
            viewModel.eventFlow.collect { event ->
                when (event) {
                    WriteReviewEvent.Done -> navigator.back()
                    is WriteReviewEvent.Failed -> failure = event.error
                }
            }
        }
        ErrorSnackbarEffect(failure, snackbar) { failure = null }
        WriteReviewScreen(
            name = route.name,
            ofSeller = route.kind == "seller",
            state = state,
            snackbar = snackbar,
            actions =
                WriteReviewActions(
                    onBack = navigator::back,
                    onRating = viewModel::setRating,
                    onTitle = viewModel::setTitle,
                    onBody = viewModel::setBody,
                    onSubmit = viewModel::submit,
                    onDelete = viewModel::delete,
                    onRetry = viewModel::load,
                ),
        )
    }
}
