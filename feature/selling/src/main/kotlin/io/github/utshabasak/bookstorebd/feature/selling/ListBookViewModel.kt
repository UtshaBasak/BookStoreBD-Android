package io.github.utshabasak.bookstorebd.feature.selling

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.image.ImagePreparer
import io.github.utshabasak.bookstorebd.core.data.image.ImagePurpose
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ProfileRepository
import io.github.utshabasak.bookstorebd.core.data.repository.SellerRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.CategoryGroup
import io.github.utshabasak.bookstorebd.core.model.DiscountProblem
import io.github.utshabasak.bookstorebd.core.model.ListingDraft
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.Pricing
import io.github.utshabasak.bookstorebd.core.model.Published
import io.github.utshabasak.bookstorebd.core.ui.navigation.ListBookRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the listing form still needs before it can be sent. */
enum class ListingProblem { Photos, Title, Author, Price, Categories, Condition }

data class ListBookUiState(
    val draft: ListingDraft = ListingDraft(),
    val photos: List<Uri> = emptyList(),
    val problems: Set<ListingProblem> = emptySet(),
    val discountProblem: DiscountProblem? = null,
    val groups: List<CategoryGroup> = emptyList(),
    /** Null until the profile has loaded; false without a bKash number, which the website requires first. */
    val canSell: Boolean? = null,
    val publishing: Boolean = false,
) {
    val price: Int? get() = draft.price.toIntOrNull()

    /** What the buyer pays after the discount, for the form's preview. */
    val salePrice: Int? get() = price?.let { Pricing.salePrice(it, draft.discount) }
}

sealed interface ListBookEvent {
    data class Listed(
        val result: Published,
    ) : ListBookEvent

    data class Failed(
        val error: AppError,
    ) : ListBookEvent
}

@HiltViewModel(assistedFactory = ListBookViewModel.Factory::class)
class ListBookViewModel
    @AssistedInject
    constructor(
        @Assisted route: ListBookRoute,
        private val sellers: SellerRepository,
        private val profiles: ProfileRepository,
        private val images: ImagePreparer,
        config: ConfigRepository,
        session: UserSession,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(route: ListBookRoute): ListBookViewModel
        }

        private val form =
            MutableStateFlow(
                ListBookUiState(draft = ListingDraft(title = route.title, author = route.author, isbn = route.isbn)),
            )

        val uiState: StateFlow<ListBookUiState> =
            combine(form, profiles.profile, config.config) { state, profile, appConfig ->
                state.copy(
                    groups = appConfig?.categoryGroups.orEmpty(),
                    canSell = if (state.canSell == false) false else profile?.canSell,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, form.value)

        /** Selling needs an account, as on the website: a signed-out visitor is sent to sign in. */
        val signedIn: StateFlow<Boolean?> = session.user.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private val events = Channel<ListBookEvent>(Channel.BUFFERED)
        val eventFlow: Flow<ListBookEvent> = events.receiveAsFlow()

        init {
            viewModelScope.launch { profiles.refresh() }
        }

        /** The profile again, as after adding a bKash number. */
        fun recheck() {
            form.update { it.copy(canSell = null) }
            viewModelScope.launch { profiles.refresh() }
        }

        fun edit(change: (ListingDraft) -> ListingDraft) {
            form.update { state ->
                val draft = change(state.draft)
                state.copy(
                    draft = draft,
                    problems = if (state.problems.isEmpty()) state.problems else problemsOf(draft, state.photos),
                    discountProblem = draft.price.toIntOrNull()?.let { Pricing.problem(it, draft.discount) },
                )
            }
        }

        /**
         * A category on or off. "Others" stands alone, as on the website:
         * choosing it clears the rest, and choosing another clears it.
         */
        fun toggleCategory(name: String) =
            edit { draft ->
                val chosen = draft.categories
                val next =
                    when {
                        name in chosen -> chosen - name
                        name == OTHERS -> setOf(OTHERS)
                        else -> (chosen - OTHERS + name).take(MAX_CATEGORIES).toSet()
                    }
                draft.copy(categories = next)
            }

        fun addPhotos(uris: List<Uri>) {
            form.update { state ->
                val photos = (state.photos + uris).distinct().take(MAX_PHOTOS)
                state.copy(photos = photos, problems = if (state.problems.isEmpty()) state.problems else problemsOf(state.draft, photos))
            }
        }

        fun removePhoto(uri: Uri) = form.update { it.copy(photos = it.photos - uri) }

        /** Moves a photo to the front: the first one is the cover. */
        fun makeCover(uri: Uri) = form.update { it.copy(photos = listOf(uri) + (it.photos - uri)) }

        fun publish() {
            val state = form.value
            val problems = problemsOf(state.draft, state.photos)
            val discountProblem = state.price?.let { Pricing.problem(it, state.draft.discount) }
            if (problems.isNotEmpty() || discountProblem != null || state.publishing) {
                form.update { it.copy(problems = problems, discountProblem = discountProblem) }
                return
            }
            viewModelScope.launch {
                form.update { it.copy(publishing = true, problems = emptySet()) }
                val uploads = state.photos.mapNotNull { images.prepare(it, ImagePurpose.Photo) }
                val result =
                    if (uploads.size < state.photos.size) {
                        Outcome.Failure(AppError.Unexpected(IllegalStateException("A photo could not be read")))
                    } else {
                        sellers.publish(state.draft, uploads)
                    }
                form.update { it.copy(publishing = false) }
                when (result) {
                    is Outcome.Success -> {
                        events.send(ListBookEvent.Listed(result.value))
                    }

                    is Outcome.Failure -> {
                        if ((result.error as? AppError.Rejected)?.code == PAYOUT_NUMBER_REQUIRED) form.update { it.copy(canSell = false) }
                        events.send(ListBookEvent.Failed(result.error))
                    }
                }
            }
        }

        private fun problemsOf(
            draft: ListingDraft,
            photos: List<Uri>,
        ): Set<ListingProblem> =
            buildSet {
                if (photos.isEmpty()) add(ListingProblem.Photos)
                if (draft.title.isBlank()) add(ListingProblem.Title)
                if (draft.author.isBlank()) add(ListingProblem.Author)
                if (draft.price.toIntOrNull() == null) add(ListingProblem.Price)
                if (draft.categories.isEmpty()) add(ListingProblem.Categories)
                // A second-hand copy says what state it is in; a new book has no condition.
                if (draft.type == BookType.Old && draft.condition.isBlank()) add(ListingProblem.Condition)
            }

        companion object {
            const val MAX_PHOTOS = 10
            const val MAX_CATEGORIES = 20
            const val OTHERS = "Others"
            private const val PAYOUT_NUMBER_REQUIRED = "payout-number-required"
        }
    }
