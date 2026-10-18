package io.github.utshabasak.bookstorebd.feature.catalogue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.CartRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CatalogueRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.SettingsRepository
import io.github.utshabasak.bookstorebd.core.data.repository.WishlistRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.CatalogueQuery
import io.github.utshabasak.bookstorebd.core.model.CatalogueSort
import io.github.utshabasak.bookstorebd.core.model.CategoryGroup
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.SellerHit
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

    data object SignInForCart : CatalogueEvent

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
        private val cart: CartRepository,
        private val session: UserSession,
        config: ConfigRepository,
        private val settings: SettingsRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(route: CatalogueRoute): CatalogueViewModel
        }

        private val mutableQuery = MutableStateFlow(route.toQuery())
        val query: StateFlow<CatalogueQuery> = mutableQuery.asStateFlow()

        private val mutableText = MutableStateFlow(route.search)
        val searchText: StateFlow<String> = mutableText.asStateFlow()

        private val mutableTotal = MutableStateFlow<Int?>(null)

        /** How many books match in all, for "Results for X · N books"; null until the first page is in. */
        val total: StateFlow<Int?> = mutableTotal.asStateFlow()

        val results: Flow<PagingData<Book>> =
            mutableQuery
                .flatMapLatest { query ->
                    mutableTotal.value = null
                    catalogue.search(query) { mutableTotal.value = it }
                }.cachedIn(viewModelScope)

        /** Sellers whose name matches the search, above the books, as on the website. */
        val sellers: StateFlow<List<SellerHit>> =
            mutableQuery
                .map { it.search.trim() }
                .distinctUntilChanged()
                .mapLatest { text ->
                    if (text.length <
                        MIN_SELLER_SEARCH
                    ) {
                        emptyList()
                    } else {
                        catalogue.matchingSellers(text).getOrNull().orEmpty()
                    }
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        /** The language voice search listens in; null until chosen, when the app's own language is used. */
        val voiceLanguage: StateFlow<String?> =
            settings.voiceLanguage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

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

        /** The books in the cart, for the cart button on every card. */
        val cartIds: StateFlow<Set<String>> =
            cart.quantities.map { it.keys }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

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
            val current = mutableQuery.value
            mutableQuery.value =
                filters.copy(
                    search = current.search,
                    sort = sortAfter(current.sort, filters.dealsOnly),
                    // Condition is for second-hand books: it goes with the type, rather than staying on out of sight.
                    condition = filters.condition.takeIf { filters.type == BookType.Old },
                )
        }

        fun clearFilters() {
            val current = mutableQuery.value
            mutableQuery.value = CatalogueQuery(search = current.search, sort = sortAfter(current.sort, dealsOnly = false))
        }

        fun setSort(sort: CatalogueSort) {
            mutableQuery.value = mutableQuery.value.copy(sort = sort)
        }

        fun forgetSearch(term: String) {
            viewModelScope.launch { catalogue.forgetSearch(term) }
        }

        fun clearRecentSearches() {
            viewModelScope.launch { catalogue.clearRecentSearches() }
        }

        fun setVoiceLanguage(tag: String) {
            viewModelScope.launch { settings.setVoiceLanguage(tag) }
        }

        /** The cart button on a card: in, or out again, as on the website. */
        fun toggleCart(book: Book) {
            viewModelScope.launch {
                if (session.currentUser() == null) {
                    events.send(CatalogueEvent.SignInForCart)
                    return@launch
                }
                val result = cart.toggle(book.id, inCart = book.id in cartIds.value)
                if (result is Outcome.Failure) events.send(CatalogueEvent.Failed(result.error))
            }
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
            const val MIN_SELLER_SEARCH = 2
        }
    }

/** A deal sort shows deals only, so turning deals off goes back to the usual order as well, as on the website. */
internal fun sortAfter(
    sort: CatalogueSort,
    dealsOnly: Boolean,
): CatalogueSort =
    if (!dealsOnly &&
        (sort == CatalogueSort.DealPercent || sort == CatalogueSort.DealAmount)
    ) {
        CatalogueSort.Relevant
    } else {
        sort
    }
