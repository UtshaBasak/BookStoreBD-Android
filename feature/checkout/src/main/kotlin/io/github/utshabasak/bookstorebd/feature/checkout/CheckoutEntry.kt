package io.github.utshabasak.bookstorebd.feature.checkout

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatTaka
import io.github.utshabasak.bookstorebd.core.ui.navigation.CheckoutRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.HomeRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrderPlacedRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrderRoute
import io.github.utshabasak.bookstorebd.core.ui.readableWidth
import kotlinx.coroutines.launch

/** Registers checkout and its confirmation with the app's navigation. */
fun EntryProviderScope<NavKey>.checkoutEntries(navigator: Navigator) {
    entry<CheckoutRoute> {
        val viewModel: CheckoutViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(viewModel) {
            viewModel.eventFlow.collect { event ->
                when (event) {
                    is CheckoutEvent.Placed -> {
                        navigator.replace(
                            OrderPlacedRoute(
                                orderNumber = event.order.orderNumber,
                                total = event.total,
                                unavailable = event.order.unavailable.map { it.toLeftOut() },
                                promoMessage = event.order.promoMessage,
                                promoCode = event.promoCode,
                            ),
                        )
                    }
                }
            }
        }
        ErrorSnackbarEffect(state.error, snackbar, viewModel::errorShown)
        CheckoutScreen(
            state = state,
            snackbar = snackbar,
            onUpdate = viewModel::update,
            onApplyPromo = viewModel::applyPromo,
            onRemovePromo = viewModel::removePromo,
            onPlaceOrder = viewModel::placeOrder,
            onBack = navigator::back,
            cartActions = CheckoutCartActions(onQuantity = viewModel::setQuantity, onRemove = viewModel::remove),
        )
    }
    entry<OrderPlacedRoute> { route ->
        OrderPlacedDestination(
            route = route,
            onTrack = { navigator.replace(OrderRoute(route.orderNumber)) },
            onContinue = { navigator.replace(HomeRoute) },
        )
    }
}
