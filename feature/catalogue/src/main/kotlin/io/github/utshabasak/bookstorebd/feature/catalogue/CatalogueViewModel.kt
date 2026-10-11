package io.github.utshabasak.bookstorebd.feature.catalogue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.CatalogueRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.WishlistRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.CatalogueQuery
import io.github.utshabasak.bookstorebd.core.model.CatalogueSort
import io.github.utshabasak.bookstorebd.core.model.CategoryGroup
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.Suggestions
import io.github.utshabasak.bookstorebd.core.model.getOrNull
import io.github.utshabasak.bookstorebd.core.ui.navigation.CatalogueRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CatalogueEvent {
    data object SignInToSave : CatalogueEvent

    data class Failed(
        val error: AppError,
    ) : CatalogueEvent
}

/** The catalogue: one search with its filters, the suggestions while typing, and the results a page at a time. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel(assistedFactory = CatalogueViewModel.Factory::class)
class CatalogueViewModel
    @AssistedInject
    constructor(
        @Assisted route: CatalogueRoute,
        private val catalogue: CatalogueRepository,
        private val wishlist: WishlistRepository,
        private val session: UserSession,
        config: ConfigRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(route: CatalogueRoute): CatalogueViewModel
        }

        private val mutableQuery = MutableStateFlow(route.toQuery())
        val query: StateFlow<CatalogueQuery> = mutableQuery.asStateFlow()

        private val mutableText = MutableStateFlow(route.search)
        val searchText: StateFlow<String> = mutableText.asStateFlow()

        val results: Flow<PagingData<Book>> = mutableQuery.flatMapLatest { catalogue.search(it) }.cachedIn(viewModelScope)

        /** Suggestions for what is being typed; null while the box is empty. */
        val suggestions: StateFlow<Suggestions?> =
            mutableText
                .map { it.trim() }
                .distinctUntilChanged()
                .debounce(SUGGEST_DEBOUNCE_MS)
                .mapLatest { text -> if (text.isEmpty()) null else catalogue.suggest(text).getOrNull() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        val recentSearches: StateFlow<List<String>> =
            catalogue.recentSearches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        private val mutablePopular = MutableStateFlow<List<String>>(emptyList())
        val popularSearches: StateFlow<List<String>> = mutablePopular.asStateFlow()

        val categoryGroups: StateFlow<List<CategoryGroup>> =
            config.config
                .map { it?.categoryGroups.orEmpty() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        val wishlistIds: StateFlow<Set<String>> =
            wishlist.ids.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        private val events = Channel<CatalogueEvent>(Channel.BUFFERED)
        val eventFlow: Flow<CatalogueEvent> = events.receiveAsFlow()

        init {
            viewModelScope.launch { mutablePopular.value = catalogue.popularSearches().getOrNull().orEmpty() }
        }

        fun onSearchTextChange(text: String) {
            mutableText.value = text.take(MAX_SEARCH_LENGTH)
        }

        /** Runs the search for what is in the box (or [text]), keeping the filters, and remembers it. */
        fun search(text: String = mutableText.value) {
            val clean = text.trim()
            mutableText.value = clean
            mutableQuery.value = mutableQuery.value.copy(search = clean)
            viewModelScope.launch { catalogue.rememberSearch(clean) }
        }

        /** An author or a writer from the suggestions: searched by name, as the website does. */
        fun searchAuthor(name: String) = search(name)

        fun filterByCategory(category: String) {
            mutableText.value = ""
            mutableQuery.value = mutableQuery.value.copy(search = "", categories = setOf(category))
        }

        fun applyFilters(filters: CatalogueQuery) {
            mutableQuery.value = filters.copy(search = mutableQuery.value.search, sort = mutableQuery.value.sort)
        }

        fun clearFilters() {
            mutableQuery.value = CatalogueQuery(search = mutableQuery.value.search, sort = mutableQuery.value.sort)
        }

        fun setSort(sort: CatalogueSort) {
            mutableQuery.value = mutableQuery.value.copy(sort = sort)
        }

        fun forgetSearch(term: String) {
            viewModelScope.launch { catalogue.forgetSearch(term) }
        }

        fun toggleWishlist(book: Book) {
            viewModelScope.launch {
                if (session.currentUser() == null) {
                    events.send(CatalogueEvent.SignInToSave)
                    return@launch
                }
                val result = if (book.id in wishlistIds.value) wishlist.remove(book.id) else wishlist.add(book.id)
                if (result is Outcome.Failure) events.send(CatalogueEvent.Failed(result.error))
            }
        }

        private companion object {
            const val SUGGEST_DEBOUNCE_MS = 250L
            const val STOP_TIMEOUT_MS = 5_000L
            const val MAX_SEARCH_LENGTH = 100
        }
    }
