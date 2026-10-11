package io.github.utshabasak.bookstorebd.feature.orders

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.image.ImagePreparer
import io.github.utshabasak.bookstorebd.core.data.image.ImagePurpose
import io.github.utshabasak.bookstorebd.core.data.repository.OrderRepository
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.ReturnDraft
import io.github.utshabasak.bookstorebd.core.ui.FormWidth
import io.github.utshabasak.bookstorebd.core.ui.navigation.ReturnRoute
import io.github.utshabasak.bookstorebd.core.ui.onHardwareEnter
import io.github.utshabasak.bookstorebd.core.ui.readableWidth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReturnUiState(
    val titles: List<String> = emptyList(),
    val description: String = "",
    val bkash: String = "",
    val photos: List<Uri> = emptyList(),
    val sending: Boolean = false,
    val problems: Set<ReturnProblem> = emptySet(),
)

enum class ReturnProblem { Description, Bkash, Photos }

sealed interface ReturnEvent {
    data class Sent(
        val message: String,
    ) : ReturnEvent

    data class Failed(
        val error: AppError,
    ) : ReturnEvent
}

/**
 * Asking to return books from a delivered order: what is wrong, a bKash number
 * for the refund, and one to ten photos, as on the website's return form.
 */
@HiltViewModel(assistedFactory = ReturnViewModel.Factory::class)
class ReturnViewModel
    @AssistedInject
    constructor(
        @Assisted private val route: ReturnRoute,
        private val orders: OrderRepository,
        private val images: ImagePreparer,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(route: ReturnRoute): ReturnViewModel
        }

        private val state = MutableStateFlow(ReturnUiState(titles = route.titles))
        val uiState: StateFlow<ReturnUiState> = state.asStateFlow()

        private val events = Channel<ReturnEvent>(Channel.BUFFERED)
        val eventFlow: Flow<ReturnEvent> = events.receiveAsFlow()

        fun setDescription(text: String) =
            state.update {
                it.copy(
                    description = text.take(DESCRIPTION_LIMIT),
                    problems =
                        it.problems - ReturnProblem.Description,
                )
            }

        fun setBkash(text: String) = state.update { it.copy(bkash = text, problems = it.problems - ReturnProblem.Bkash) }

        fun addPhotos(uris: List<Uri>) =
            state.update { current ->
                current.copy(
                    photos = (current.photos + uris).distinct().take(MAX_PHOTOS),
                    problems =
                        current.problems - ReturnProblem.Photos,
                )
            }

        fun removePhoto(uri: Uri) = state.update { it.copy(photos = it.photos - uri) }

        fun send() {
            val current = state.value
            if (current.sending) return
            val bkash = current.bkash.filter(Char::isDigit).let { if (it.startsWith("880")) it.removePrefix("88") else it }
            val problems =
                buildSet {
                    if (current.description.isBlank()) add(ReturnProblem.Description)
                    if (!Regex("^01[3-9]\\d{8}$").matches(bkash)) add(ReturnProblem.Bkash)
                    if (current.photos.isEmpty()) add(ReturnProblem.Photos)
                }
            if (problems.isNotEmpty()) {
                state.update { it.copy(problems = problems) }
                return
            }
            viewModelScope.launch {
                state.update { it.copy(sending = true) }
                val prepared = current.photos.mapNotNull { images.prepare(it, ImagePurpose.Photo) }
                val result = orders.requestReturn(ReturnDraft(route.lineIds, current.description, bkash, prepared))
                state.update { it.copy(sending = false) }
                when (result) {
                    is Outcome.Success -> {
                        orders.refreshOrder(route.orderNumber)
                        events.send(ReturnEvent.Sent(result.value))
                    }

                    is Outcome.Failure -> {
                        events.send(ReturnEvent.Failed(result.error))
                    }
                }
            }
        }

        companion object {
            const val MAX_PHOTOS = 10
            private const val DESCRIPTION_LIMIT = 2000
        }
    }

data class ReturnActions(
    val onBack: () -> Unit,
    val onDescription: (String) -> Unit,
    val onBkash: (String) -> Unit,
    val onAddPhotos: () -> Unit,
    val onRemovePhoto: (Uri) -> Unit,
    val onSend: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnScreen(
    state: ReturnUiState,
    actions: ReturnActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_orders_return_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_orders_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().readableWidth(FormWidth),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        pluralStringResource(R.plurals.feature_orders_returning, state.titles.size, state.titles.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    state.titles.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            item {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = actions.onDescription,
                    label = { Text(stringResource(R.string.feature_orders_return_what)) },
                    placeholder = { Text(stringResource(R.string.feature_orders_return_what_hint)) },
                    isError = ReturnProblem.Description in state.problems,
                    supportingText = {
                        if (ReturnProblem.Description in
                            state.problems
                        ) {
                            Text(stringResource(R.string.feature_orders_return_what_problem))
                        }
                    },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = state.bkash,
                    onValueChange = actions.onBkash,
                    label = { Text(stringResource(R.string.feature_orders_return_bkash)) },
                    isError = ReturnProblem.Bkash in state.problems,
                    supportingText = {
                        Text(
                            stringResource(
                                if (ReturnProblem.Bkash in
                                    state.problems
                                ) {
                                    R.string.feature_orders_return_bkash_problem
                                } else {
                                    R.string.feature_orders_return_bkash_help
                                },
                            ),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().onHardwareEnter(actions.onSend),
                )
            }
            item { Photos(state, actions) }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.feature_orders_return_next), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.feature_orders_return_next_body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Button(onClick = actions.onSend, enabled = !state.sending, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    if (state.sending) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.feature_orders_return_send))
                    }
                }
            }
        }
    }
}

@Composable
private fun Photos(
    state: ReturnUiState,
    actions: ReturnActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.feature_orders_return_photos), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(
                if (ReturnProblem.Photos in
                    state.problems
                ) {
                    R.string.feature_orders_return_photos_problem
                } else {
                    R.string.feature_orders_return_photos_help
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (ReturnProblem.Photos in state.problems) MaterialTheme.colorScheme.error else BookStoreTheme.colors.textMuted,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.photos, key = { it.toString() }) { uri ->
                Box(Modifier.size(PHOTO_SIZE)) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                    )
                    FilledTonalIconButton(onClick = {
                        actions.onRemovePhoto(uri)
                    }, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.feature_orders_return_remove_photo),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            if (state.photos.size < ReturnViewModel.MAX_PHOTOS) {
                item {
                    OutlinedButton(onClick = actions.onAddPhotos, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(PHOTO_SIZE)) {
                        Icon(Icons.Outlined.AddAPhoto, contentDescription = stringResource(R.string.feature_orders_return_add_photos))
                    }
                }
            }
        }
    }
}

private val PHOTO_SIZE = 96.dp
