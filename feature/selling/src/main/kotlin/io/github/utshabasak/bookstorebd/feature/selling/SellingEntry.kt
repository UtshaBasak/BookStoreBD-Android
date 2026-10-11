package io.github.utshabasak.bookstorebd.feature.selling

import android.content.Context
import android.net.Uri
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Order
import io.github.utshabasak.bookstorebd.core.model.SetupItem
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.PendingMessage
import io.github.utshabasak.bookstorebd.core.ui.PendingMessageEffect
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatDate
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.EditProfileRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.ListBookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.MyBooksRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.SaleRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SalesRoute
import io.github.utshabasak.bookstorebd.core.ui.writeReceiptPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/** Registers listing a book, the seller's books, their orders and one order. */
fun EntryProviderScope<NavKey>.sellingEntries(navigator: Navigator) {
    entry<ListBookRoute> { route -> ListBookDestination(route, navigator) }
    entry<MyBooksRoute> { MyBooksDestination(navigator) }
    entry<SalesRoute> { SalesDestination(navigator) }
    entry<SaleRoute> { route -> SaleDestination(route, navigator) }
}

@Composable
private fun ListBookDestination(
    route: ListBookRoute,
    navigator: Navigator,
) {
    val viewModel = hiltViewModel<ListBookViewModel, ListBookViewModel.Factory>(creationCallback = { it.create(route) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var failure by remember { mutableStateOf<AppError?>(null) }
    var listed by remember { mutableStateOf<ListBookEvent.Listed?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(ListBookViewModel.MAX_PHOTOS)) { uris ->
            viewModel.addPhotos(uris)
        }
    // Back from adding a bKash number: the form can be sent now.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (state.canSell == false) viewModel.recheck() }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ListBookEvent.Listed -> listed = event
                is ListBookEvent.Failed -> failure = event.error
            }
        }
    }
    listed?.let { event ->
        val text = listedMessage(event)
        LaunchedEffect(event) {
            PendingMessage.post(text)
            navigator.replace(MyBooksRoute)
        }
    }
    ErrorSnackbarEffect(failure, snackbar) { failure = null }
    ListBookScreen(
        state = state,
        snackbar = snackbar,
        actions =
            ListBookActions(
                onBack = navigator::back,
                onEdit = viewModel::edit,
                onToggleCategory = viewModel::toggleCategory,
                onAddPhotos = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemovePhoto = viewModel::removePhoto,
                onMakeCover = viewModel::makeCover,
                onAddBkash = { navigator.navigate(EditProfileRoute(SetupItem.Bkash.name)) },
                onPublish = viewModel::publish,
            ),
    )
}

/** "Your book is listed", and how many people waiting for it on the Wanted board were told. */
@Composable
private fun listedMessage(event: ListBookEvent.Listed): String {
    val waiting = event.result.waiting
    return if (waiting > 0) {
        pluralStringResource(R.plurals.feature_selling_listed_waiting, waiting, formatCount(waiting, currentLocale()))
    } else {
        stringResource(R.string.feature_selling_listed)
    }
}

@Composable
private fun MyBooksDestination(navigator: Navigator) {
    val viewModel: MyBooksViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var failure by remember { mutableStateOf<AppError?>(null) }
    val saved = stringResource(R.string.feature_selling_saved)
    PendingMessageEffect(snackbar)
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is MyBooksEvent.Saved -> snackbar.showSnackbar(saved)
                is MyBooksEvent.Deleted -> snackbar.showSnackbar(event.message)
                is MyBooksEvent.NotSaved -> failure = event.error
                is MyBooksEvent.Failed -> failure = event.error
            }
        }
    }
    ErrorSnackbarEffect(failure, snackbar) { failure = null }
    MyBooksScreen(
        state = state,
        snackbar = snackbar,
        actions =
            MyBooksActions(
                onBack = navigator::back,
                onRefresh = viewModel::refresh,
                onSearch = viewModel::setSearch,
                onListBook = { navigator.navigate(ListBookRoute()) },
                onOpenBook = { navigator.navigate(BookRoute(it)) },
                onSave = viewModel::save,
                onDelete = viewModel::delete,
            ),
    )
}

@Composable
private fun SalesDestination(navigator: Navigator) {
    val viewModel: SalesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sales = viewModel.sales.collectAsLazyPagingItems()
    // Fresh when coming back from an order whose status changed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { sales.refresh() }
    SalesScreen(
        state = state,
        sales = sales,
        actions =
            SalesActions(
                onBack = navigator::back,
                onSearch = viewModel::setSearch,
                onStatus = viewModel::setStatus,
                onSort = viewModel::setSort,
                onOpen = { navigator.navigate(SaleRoute(it)) },
            ),
    )
}

@Composable
private fun SaleDestination(
    route: SaleRoute,
    navigator: Navigator,
) {
    val viewModel = hiltViewModel<SaleViewModel, SaleViewModel.Factory>(creationCallback = { it.create(route.orderNumber) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val returnDays by viewModel.returnWindowDays.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var failure by remember { mutableStateOf<AppError?>(null) }
    val updated = stringResource(R.string.feature_selling_status_updated)
    val slipSaved = stringResource(R.string.feature_selling_slip_saved)
    val slipFailed = stringResource(R.string.feature_selling_slip_failed)
    var slipOf by remember { mutableStateOf<Order?>(null) }
    val saveSlip =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF)) { uri ->
            val order = slipOf
            if (uri != null && order != null) {
                scope.launch { snackbar.showSnackbar(if (saveSlip(context, order, returnDays, uri)) slipSaved else slipFailed) }
            }
        }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                SaleEvent.StatusChanged -> snackbar.showSnackbar(updated)
                is SaleEvent.Message -> snackbar.showSnackbar(event.text)
                is SaleEvent.Failed -> failure = event.error
            }
        }
    }
    ErrorSnackbarEffect(failure, snackbar) { failure = null }
    SaleScreen(
        orderNumber = route.orderNumber,
        state = state,
        snackbar = snackbar,
        actions =
            SaleActions(
                onBack = navigator::back,
                onRefresh = viewModel::refresh,
                onStatus = viewModel::setStatus,
                onCancel = viewModel::cancel,
                onSlip = { order ->
                    slipOf = order
                    saveSlip.launch("BookStoreBD-order-${order.orderNumber}-seller.pdf")
                },
            ),
    )
}

/** Writes the seller's slip where they chose, off the main thread; false if it could not be written. */
private suspend fun saveSlip(
    context: Context,
    order: Order,
    returnDays: Int,
    uri: Uri,
): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val locale = context.resources.configuration.locales[0]
            val slip =
                buildSellerSlip(
                    order,
                    context.resources,
                    locale,
                    returnDays,
                    formatDate(Instant.now().toString(), locale, withTime = true),
                )
            context.contentResolver.openOutputStream(uri)?.use { writeReceiptPdf(slip, it) } ?: error("No output")
        }.isSuccess
    }

private const val PDF = "application/pdf"
