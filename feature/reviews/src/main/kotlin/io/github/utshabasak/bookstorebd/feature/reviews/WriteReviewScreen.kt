package io.github.utshabasak.bookstorebd.feature.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.CommunityRepository
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.ReviewBlock
import io.github.utshabasak.bookstorebd.core.model.ReviewDraft
import io.github.utshabasak.bookstorebd.core.model.ReviewTarget
import io.github.utshabasak.bookstorebd.core.ui.EmptyState
import io.github.utshabasak.bookstorebd.core.ui.ErrorState
import io.github.utshabasak.bookstorebd.core.ui.FormWidth
import io.github.utshabasak.bookstorebd.core.ui.LoadingState
import io.github.utshabasak.bookstorebd.core.ui.navigation.WriteReviewRoute
import io.github.utshabasak.bookstorebd.core.ui.readableWidth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WriteReviewUiState(
    val loaded: Boolean = false,
    val loadError: AppError? = null,
    val canReview: Boolean = false,
    val block: ReviewBlock? = null,
    /** True when editing a review written before. */
    val editing: Boolean = false,
    val rating: Int = 0,
    val title: String = "",
    val body: String = "",
    val busy: Boolean = false,
    val needsRating: Boolean = false,
)

sealed interface WriteReviewEvent {
    data object Done : WriteReviewEvent

    data class Failed(
        val error: AppError,
    ) : WriteReviewEvent
}

/** Writing, editing or removing a review of a book, or a rating of a seller. Only verified buyers can. */
@HiltViewModel(assistedFactory = WriteReviewViewModel.Factory::class)
class WriteReviewViewModel
    @AssistedInject
    constructor(
        @Assisted route: WriteReviewRoute,
        private val community: CommunityRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(route: WriteReviewRoute): WriteReviewViewModel
        }

        private val target: ReviewTarget =
            if (route.kind ==
                "seller"
            ) {
                ReviewTarget.OfSeller(route.targetId)
            } else {
                ReviewTarget.OfBook(route.targetId)
            }

        private val state = MutableStateFlow(WriteReviewUiState())
        val uiState: StateFlow<WriteReviewUiState> = state.asStateFlow()

        private val events = Channel<WriteReviewEvent>(Channel.BUFFERED)
        val eventFlow: Flow<WriteReviewEvent> = events.receiveAsFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                when (val result = community.reviews(target)) {
                    is Outcome.Success -> {
                        val page = result.value
                        val mine = page.mine
                        state.update {
                            it.copy(
                                loaded = true,
                                loadError = null,
                                canReview = page.canReview || mine != null,
                                block = page.block,
                                editing = mine != null,
                                rating = mine?.rating ?: 0,
                                title = mine?.title.orEmpty(),
                                body = mine?.body.orEmpty(),
                            )
                        }
                    }

                    is Outcome.Failure -> {
                        state.update { it.copy(loadError = result.error) }
                    }
                }
            }
        }

        fun setRating(rating: Int) = state.update { it.copy(rating = rating, needsRating = false) }

        fun setTitle(text: String) = state.update { it.copy(title = text.take(TITLE_LIMIT)) }

        fun setBody(text: String) = state.update { it.copy(body = text.take(BODY_LIMIT)) }

        fun submit() {
            val current = state.value
            if (current.busy) return
            if (current.rating !in 1..STARS) {
                state.update { it.copy(needsRating = true) }
                return
            }
            run { community.writeReview(target, ReviewDraft(current.rating, current.title, current.body)) }
        }

        fun delete() = run { community.deleteReview(target) }

        private fun run(block: suspend () -> Outcome<Unit>) {
            viewModelScope.launch {
                state.update { it.copy(busy = true) }
                val result = block()
                state.update { it.copy(busy = false) }
                events.send(if (result is Outcome.Failure) WriteReviewEvent.Failed(result.error) else WriteReviewEvent.Done)
            }
        }

        companion object {
            const val STARS = 5
            private const val TITLE_LIMIT = 200
            private const val BODY_LIMIT = 2000
        }
    }

data class WriteReviewActions(
    val onBack: () -> Unit,
    val onRating: (Int) -> Unit,
    val onTitle: (String) -> Unit,
    val onBody: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onDelete: () -> Unit,
    val onRetry: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteReviewScreen(
    name: String,
    ofSeller: Boolean,
    state: WriteReviewUiState,
    actions: WriteReviewActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(screenTitle(ofSeller, state.editing))) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_reviews_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            !state.loaded && state.loadError != null -> {
                ErrorState(state.loadError, onRetry = actions.onRetry, modifier = Modifier.padding(padding))
            }

            !state.loaded -> {
                LoadingState(Modifier.padding(padding))
            }

            !state.canReview -> {
                EmptyState(
                    icon = Icons.Outlined.StarOutline,
                    title = stringResource(R.string.feature_reviews_cannot_title),
                    body = stringResource(cannotReason(state.block, ofSeller)),
                    modifier = Modifier.padding(padding),
                    actionLabel = stringResource(R.string.feature_reviews_back),
                    onAction = actions.onBack,
                )
            }

            else -> {
                ReviewForm(name, ofSeller, state, actions, Modifier.padding(padding)) { confirmDelete = true }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.feature_reviews_remove_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.onDelete()
                }) { Text(stringResource(R.string.feature_reviews_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.feature_reviews_keep)) } },
        )
    }
}

@Composable
private fun ReviewForm(
    name: String,
    ofSeller: Boolean,
    state: WriteReviewUiState,
    actions: WriteReviewActions,
    modifier: Modifier = Modifier,
    onAskDelete: () -> Unit,
) {
    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).readableWidth(FormWidth).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        StarInput(state.rating, actions.onRating)
        if (state.needsRating) {
            Text(
                stringResource(R.string.feature_reviews_pick_stars),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedTextField(
            value = state.title,
            onValueChange = actions.onTitle,
            label = { Text(stringResource(R.string.feature_reviews_title_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.body,
            onValueChange = actions.onBody,
            label = {
                Text(stringResource(if (ofSeller) R.string.feature_reviews_body_seller else R.string.feature_reviews_body_book))
            },
            minLines = 4,
            supportingText = { Text(stringResource(R.string.feature_reviews_public)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = actions.onSubmit, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(if (state.editing) R.string.feature_reviews_save else R.string.feature_reviews_post))
            }
        }
        if (state.editing) {
            OutlinedButton(
                onClick = onAskDelete,
                enabled = !state.busy,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.feature_reviews_remove)) }
        }
    }
}

private fun screenTitle(
    ofSeller: Boolean,
    editing: Boolean,
): Int =
    when {
        ofSeller && editing -> R.string.feature_reviews_edit_rating
        ofSeller -> R.string.feature_reviews_rate_seller
        editing -> R.string.feature_reviews_edit_review
        else -> R.string.feature_reviews_write_review
    }

private fun cannotReason(
    block: ReviewBlock?,
    ofSeller: Boolean,
): Int =
    when (block) {
        ReviewBlock.SignIn -> R.string.feature_reviews_cannot_sign_in
        ReviewBlock.OwnListing -> R.string.feature_reviews_cannot_own_listing
        ReviewBlock.OwnShop -> R.string.feature_reviews_cannot_own_shop
        else if ofSeller -> R.string.feature_reviews_cannot_not_bought_seller
        else -> R.string.feature_reviews_cannot_not_bought
    }

/** Five stars, each a 48 dp target; TalkBack reads "3 stars, selected". */
@Composable
private fun StarInput(
    rating: Int,
    onRating: (Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
        for (star in 1..WriteReviewViewModel.STARS) {
            val label = pluralStringResource(R.plurals.feature_reviews_stars, star, star)
            Icon(
                if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = label,
                tint = if (star <= rating) BookStoreTheme.colors.star else MaterialTheme.colorScheme.outline,
                modifier =
                    Modifier
                        .size(48.dp)
                        .padding(6.dp)
                        .selectable(selected = star == rating, role = Role.RadioButton) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onRating(star)
                        },
            )
        }
    }
}
