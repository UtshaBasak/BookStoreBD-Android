package io.github.utshabasak.bookstorebd.feature.wanted

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignInRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.WantedRoute

/** Registers the Wanted board. */
fun EntryProviderScope<NavKey>.wantedEntry(navigator: Navigator) {
    entry<WantedRoute> { route ->
        val viewModel = hiltViewModel<WantedViewModel, WantedViewModel.Factory>(creationCallback = { it.create(route.ask) })
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val items = viewModel.items.collectAsLazyPagingItems()
        val snackbar = remember { SnackbarHostState() }
        var failure by remember { mutableStateOf<AppError?>(null) }
        val seeIt = stringResource(R.string.feature_wanted_see_it)
        LaunchedEffect(viewModel) {
            viewModel.eventFlow.collect { event ->
                when (event) {
                    is WantedEvent.Message -> {
                        val bookId = event.bookId
                        val result =
                            snackbar.showSnackbar(
                                event.text,
                                actionLabel =
                                    bookId?.let {
                                        seeIt
                                    },
                                duration = SnackbarDuration.Long,
                            )
                        if (result == SnackbarResult.ActionPerformed && bookId != null) navigator.navigate(BookRoute(bookId))
                    }

                    is WantedEvent.Failed -> {
                        failure = event.error
                    }

                    WantedEvent.SignInNeeded -> {
                        navigator.navigate(SignInRoute)
                    }
                }
            }
        }
        ErrorSnackbarEffect(failure, snackbar) { failure = null }
        WantedScreen(
            state = state,
            items = items,
            snackbar = snackbar,
            actions =
                WantedActions(
                    onBack = navigator::back,
                    onSearch = viewModel::setSearch,
                    onStatus = viewModel::setStatus,
                    onSort = viewModel::setSort,
                    onMineOnly = viewModel::setMineOnly,
                    onToggle = viewModel::toggle,
                    onOpenBook = { navigator.navigate(BookRoute(it)) },
                    onOpenForm = viewModel::openForm,
                    onCloseForm = viewModel::closeForm,
                    onEditForm = viewModel::editForm,
                    onAsk = viewModel::ask,
                ),
        )
    }
}
