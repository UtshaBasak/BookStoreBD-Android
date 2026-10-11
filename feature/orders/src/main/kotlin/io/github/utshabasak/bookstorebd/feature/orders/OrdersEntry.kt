package io.github.utshabasak.bookstorebd.feature.orders

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.PendingMessage
import io.github.utshabasak.bookstorebd.core.ui.PendingMessageEffect
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.HomeRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrderRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrdersRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.ReturnRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignInRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.WriteReviewRoute
import kotlinx.coroutines.launch

/** Registers the order list, an order's tracking page, and the return form. */
fun EntryProviderScope<NavKey>.ordersEntries(navigator: Navigator) {
    entry<OrdersRoute> { route -> OrdersDestination(route, navigator) }

    entry<OrderRoute> { route -> OrderDestination(route, navigator) }

    entry<ReturnRoute> { route -> ReturnDestination(route, navigator) }
}

@Composable
private fun OrdersDestination(
    route: OrdersRoute,
    navigator: Navigator,
) {
    val viewModel: OrdersViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val orders = viewModel.orders.collectAsLazyPagingItems()
    val books = viewModel.books.collectAsLazyPagingItems()
    val snackbar = remember { SnackbarHostState() }
    var seen by rememberSaveable { mutableStateOf(false) }
    // Coming back from an order or a return: show what changed.
    LaunchedEffect(Unit) { if (seen) orders.refresh() else seen = true }
    PendingMessageEffect(snackbar)
    LaunchedEffect(signedIn) { if (signedIn == false) navigator.replace(SignInRoute) }
    OrdersScreen(
        state = state,
        orders = orders,
        books = books,
        initialBooksTab = route.booksTab,
        snackbar = snackbar,
        actions =
            OrdersActions(
                onBack = navigator::back,
                onSearch = viewModel::setSearch,
                onStatus = viewModel::setStatus,
                onSort = viewModel::setSort,
                onOpenOrder = { navigator.navigate(OrderRoute(it)) },
                onOpenBook = { navigator.navigate(BookRoute(it)) },
                onReturn = { item ->
                    navigator.navigate(ReturnRoute(item.order.orderNumber, listOf(item.line.id), listOf(item.line.title)))
                },
                onReview = { item -> navigator.navigate(WriteReviewRoute("book", item.line.bookId, item.line.title)) },
                onShop = { navigator.replace(HomeRoute) },
            ),
    )
}

@Composable
private fun OrderDestination(
    route: OrderRoute,
    navigator: Navigator,
) {
    val viewModel = hiltViewModel<OrderViewModel, OrderViewModel.Factory>(creationCallback = { it.create(route.orderNumber) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var failure by remember { mutableStateOf<AppError?>(null) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.feature_orders_copied)
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is OrderEvent.Cancelled -> snackbar.showSnackbar(event.message)
                is OrderEvent.Failed -> failure = event.error
            }
        }
    }
    ErrorSnackbarEffect(failure, snackbar) { failure = null }
    OrderScreen(
        orderNumber = route.orderNumber,
        state = state,
        snackbar = snackbar,
        actions =
            OrderActions(
                onBack = navigator::back,
                onRefresh = viewModel::refresh,
                onCopy = { number ->
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(number, number)))
                        snackbar.showSnackbar(copied)
                    }
                },
                onCancel = viewModel::cancel,
                onReturn = {
                    order,
                    lines,
                    ->
                    navigator.navigate(ReturnRoute(order.orderNumber, lines.map { it.id }, lines.map { it.title }))
                },
                onOpenBook = { navigator.navigate(BookRoute(it)) },
            ),
    )
}

@Composable
private fun ReturnDestination(
    route: ReturnRoute,
    navigator: Navigator,
) {
    val viewModel = hiltViewModel<ReturnViewModel, ReturnViewModel.Factory>(creationCallback = { it.create(route) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var failure by remember { mutableStateOf<AppError?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(ReturnViewModel.MAX_PHOTOS)) { uris ->
            viewModel.addPhotos(uris)
        }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ReturnEvent.Sent -> {
                    PendingMessage.post(event.message)
                    navigator.back()
                }

                is ReturnEvent.Failed -> {
                    failure = event.error
                }
            }
        }
    }
    ErrorSnackbarEffect(failure, snackbar) { failure = null }
    ReturnScreen(
        state = state,
        snackbar = snackbar,
        actions =
            ReturnActions(
                onBack = navigator::back,
                onDescription = viewModel::setDescription,
                onBkash = viewModel::setBkash,
                onAddPhotos = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemovePhoto = viewModel::removePhoto,
                onSend = viewModel::send,
            ),
    )
}
