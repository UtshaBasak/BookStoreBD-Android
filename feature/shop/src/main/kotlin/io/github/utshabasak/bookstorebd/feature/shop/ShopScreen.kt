package io.github.utshabasak.bookstorebd.feature.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.CartRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CatalogueRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CommunityRepository
import io.github.utshabasak.bookstorebd.core.data.repository.WishlistRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.CatalogueQuery
import io.github.utshabasak.bookstorebd.core.model.CatalogueSort
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.Review
import io.github.utshabasak.bookstorebd.core.model.ReviewPage
import io.github.utshabasak.bookstorebd.core.model.ReviewTarget
import io.github.utshabasak.bookstorebd.core.model.SellerShop
import io.github.utshabasak.bookstorebd.core.model.getOrNull
import io.github.utshabasak.bookstorebd.core.model.map
import io.github.utshabasak.bookstorebd.core.ui.BookCard
import io.github.utshabasak.bookstorebd.core.ui.BookGridSkeleton
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.ErrorState
import io.github.utshabasak.bookstorebd.core.ui.LoadingState
import io.github.utshabasak.bookstorebd.core.ui.RatingLine
import io.github.utshabasak.bookstorebd.core.ui.ReviewActions
import io.github.utshabasak.bookstorebd.core.ui.ReviewsSection
import io.github.utshabasak.bookstorebd.core.ui.SkeletonBlock
import io.github.utshabasak.bookstorebd.core.ui.SkeletonScreen
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatMonthYear
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.ChatRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.MyBooksRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.ShopRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignInRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.WriteReviewRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ShopMessage {
    data class Text(
        val text: String,
    ) : ShopMessage

    /** Saving a book or using the cart needs an account; [cart] says which was tried. */
    data class SignInFirst(
        val cart: Boolean,
    ) : ShopMessage

    data class Failed(
        val error: AppError,
    ) : ShopMessage
}

/** How the shop's books are searched, sorted and filtered, as on the website. */
data class ShopFilters(
    val search: String = "",
    val sort: CatalogueSort = CatalogueSort.Relevant,
    val inStockOnly: Boolean = false,
)

/** A seller's shop front: who they are, how they are rated, and their books, searched and sorted. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ShopViewModel.Factory::class)
class ShopViewModel
    @AssistedInject
    constructor(
        @Assisted private val username: String,
        private val catalogue: CatalogueRepository,
        private val community: CommunityRepository,
        private val session: UserSession,
        private val wishlist: WishlistRepository,
        private val cart: CartRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(username: String): ShopViewModel
        }

        val shop: StateFlow<SellerShop?> = catalogue.shop(username).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

        private val mutableError = MutableStateFlow<AppError?>(null)
        val error: StateFlow<AppError?> = mutableError.asStateFlow()

        private val mutableFilters = MutableStateFlow(ShopFilters())
        val filters: StateFlow<ShopFilters> = mutableFilters.asStateFlow()

        /** The books, again whenever the filters change; typed words settle first. */
        val books: Flow<PagingData<Book>> =
            combine(
                mutableFilters.map { it.search.trim() }.distinctUntilChanged().debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS },
                mutableFilters.map { it.sort to it.inStockOnly }.distinctUntilChanged(),
            ) { search, (sort, inStock) -> CatalogueQuery(search = search, seller = username, sort = sort, inStockOnly = inStock) }
                .distinctUntilChanged()
                .flatMapLatest { catalogue.search(it) }
                .cachedIn(viewModelScope)

        val wishlistIds: StateFlow<Set<String>> = wishlist.ids.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), emptySet())
        val cartIds: StateFlow<Set<String>> =
            cart.quantities.map { it.keys }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), emptySet())

        private val mutableRatings = MutableStateFlow<ReviewPage?>(null)
        val ratings: StateFlow<ReviewPage?> = mutableRatings.asStateFlow()

        val viewerEmail: StateFlow<String?> = session.user.map { it?.email }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private val messages = Channel<ShopMessage>(Channel.BUFFERED)
        val messageFlow: Flow<ShopMessage> = messages.receiveAsFlow()

        init {
            refresh()
        }

        fun setSearch(text: String) = mutableFilters.update { it.copy(search = text.take(MAX_SEARCH)) }

        fun setSort(sort: CatalogueSort) = mutableFilters.update { it.copy(sort = sort) }

        fun setInStockOnly(on: Boolean) = mutableFilters.update { it.copy(inStockOnly = on) }

        fun toggleWishlist(book: Book) =
            signedIn(cart = false) {
                if (book.id in wishlistIds.value) wishlist.remove(book.id) else wishlist.add(book.id)
            }

        fun toggleCart(book: Book) = signedIn(cart = true) { cart.toggle(book.id, inCart = book.id in cartIds.value) }

        private fun signedIn(
            cart: Boolean,
            block: suspend () -> Outcome<Unit>,
        ) {
            viewModelScope.launch {
                if (session.currentUser() == null) {
                    messages.send(ShopMessage.SignInFirst(cart))
                    return@launch
                }
                (block() as? Outcome.Failure)?.let { messages.send(ShopMessage.Failed(it.error)) }
            }
        }

        fun refresh() {
            viewModelScope.launch {
                mutableError.value = (catalogue.refreshShop(username) as? Outcome.Failure)?.error
                refreshRatings()
            }
        }

        /** The seller's ratings, keyed by their account id, which the shop's answer carries. */
        fun refreshRatings() {
            viewModelScope.launch {
                val id = shop.filterNotNull().first().id
                community.reviews(ReviewTarget.OfSeller(id)).getOrNull()?.let { mutableRatings.value = it }
            }
        }

        fun report(
            review: Review,
            reason: String,
        ) = ratingAction { id -> community.report(ReviewTarget.OfSeller(id), review.id, reason) }

        fun reply(
            review: Review,
            text: String,
        ) = ratingAction { id -> community.reply(ReviewTarget.OfSeller(id), review.id, text).map { "" } }

        fun deleteReply(review: Review) = ratingAction { id -> community.deleteReply(ReviewTarget.OfSeller(id), review.id).map { "" } }

        private fun ratingAction(block: suspend (String) -> Outcome<String>) {
            viewModelScope.launch {
                val id = shop.value?.id ?: return@launch
                when (val result = block(id)) {
                    is Outcome.Success -> {
                        if (result.value.isNotBlank()) messages.send(ShopMessage.Text(result.value))
                        refreshRatings()
                    }

                    is Outcome.Failure -> {
                        messages.send(ShopMessage.Failed(result.error))
                    }
                }
            }
        }

        private companion object {
            const val STOP_MS = 5_000L
            const val SEARCH_DEBOUNCE_MS = 300L
            const val MAX_SEARCH = 100
        }
    }

fun EntryProviderScope<NavKey>.shopEntry(navigator: Navigator) {
    entry<ShopRoute> { route ->
        val viewModel = hiltViewModel<ShopViewModel, ShopViewModel.Factory>(creationCallback = { it.create(route.username) })
        val shop by viewModel.shop.collectAsStateWithLifecycle()
        val error by viewModel.error.collectAsStateWithLifecycle()
        val ratings by viewModel.ratings.collectAsStateWithLifecycle()
        val viewer by viewModel.viewerEmail.collectAsStateWithLifecycle()
        val filters by viewModel.filters.collectAsStateWithLifecycle()
        val wishlist by viewModel.wishlistIds.collectAsStateWithLifecycle()
        val cart by viewModel.cartIds.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { viewModel.refreshRatings() }
        ShopMessages(viewModel, snackbar, navigator)
        ShopScreen(
            username = route.username,
            shop = shop,
            error = error,
            books = viewModel.books.collectAsLazyPagingItems(),
            onBack = navigator::back,
            onRetry = viewModel::refresh,
            onOpenBook = { navigator.navigate(BookRoute(it)) },
            onMessage = { seller -> navigator.navigate(if (viewer == null) SignInRoute else ChatRoute(seller.email, seller.username)) },
            ratings = ratings,
            viewerEmail = viewer,
            snackbar = snackbar,
            ratingActions =
                ReviewActions(
                    onWrite = { shop?.let { navigator.navigate(WriteReviewRoute("seller", it.id, it.username)) } },
                    onReport = viewModel::report,
                    onReply = viewModel::reply,
                    onDeleteReply = viewModel::deleteReply,
                    onSignIn = { navigator.navigate(SignInRoute) },
                ),
            filters = filters,
            wishlist = wishlist,
            cart = cart,
            shopActions =
                ShopActions(
                    onSearch = viewModel::setSearch,
                    onSort = viewModel::setSort,
                    onInStockOnly = viewModel::setInStockOnly,
                    onToggleWishlist = viewModel::toggleWishlist,
                    onToggleCart = viewModel::toggleCart,
                    onManage = { navigator.navigate(MyBooksRoute) },
                ),
        )
    }
}

/** The shop's messages as snackbars: what happened, what failed, and when signing in comes first. */
@Composable
private fun ShopMessages(
    viewModel: ShopViewModel,
    snackbar: SnackbarHostState,
    navigator: Navigator,
) {
    val signInToSave = stringResource(R.string.feature_shop_sign_in_to_save)
    val signInForCart = stringResource(R.string.feature_shop_sign_in_for_cart)
    val signInAction = stringResource(R.string.feature_shop_sign_in)
    var failure by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.messageFlow.collect { message ->
            when (message) {
                is ShopMessage.Text -> {
                    snackbar.showSnackbar(message.text)
                }

                is ShopMessage.Failed -> {
                    failure = message.error
                }

                is ShopMessage.SignInFirst -> {
                    val text = if (message.cart) signInForCart else signInToSave
                    if (snackbar.showSnackbar(text, signInAction) == SnackbarResult.ActionPerformed) navigator.navigate(SignInRoute)
                }
            }
        }
    }
    ErrorSnackbarEffect(failure, snackbar) { failure = null }
}

/** What the shop's book list can do, beside opening a book. */
data class ShopActions(
    val onSearch: (String) -> Unit = {},
    val onSort: (CatalogueSort) -> Unit = {},
    val onInStockOnly: (Boolean) -> Unit = {},
    val onToggleWishlist: (Book) -> Unit = {},
    val onToggleCart: (Book) -> Unit = {},
    /** "Manage your books", on one's own shop. */
    val onManage: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    username: String,
    shop: SellerShop?,
    error: AppError?,
    books: LazyPagingItems<Book>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
    ratings: ReviewPage? = null,
    viewerEmail: String? = null,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    ratingActions: ReviewActions = ReviewActions({}, { _, _ -> }, { _, _ -> }, {}),
    onMessage: (SellerShop) -> Unit = {},
    filters: ShopFilters = ShopFilters(),
    wishlist: Set<String> = emptySet(),
    cart: Set<String> = emptySet(),
    shopActions: ShopActions = ShopActions(),
) {
    val own = shop != null && viewerEmail != null && shop.email.equals(viewerEmail, ignoreCase = true)
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(username) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_shop_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            shop == null && error != null -> {
                ErrorState(error, onRetry, Modifier.padding(padding))
            }

            shop == null -> {
                SkeletonScreen(Modifier.padding(padding)) {
                    Column {
                        SkeletonBlock(Modifier.fillMaxWidth().padding(16.dp).height(120.dp), RoundedCornerShape(16.dp))
                        BookGridSkeleton()
                    }
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) { ShopHeader(shop) }
                    // The website's "Chat with" button, or "Manage your books" on one's own shop.
                    if (own) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            OutlinedButton(
                                onClick = shopActions.onManage,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).heightIn(min = 48.dp),
                            ) {
                                Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.feature_shop_manage), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    } else if (shop.email.isNotBlank()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            OutlinedButton(
                                onClick = { onMessage(shop) },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).heightIn(min = 48.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(
                                    stringResource(R.string.feature_shop_message, shop.username),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ReviewsSection(
                            ratings,
                            ofSeller = true,
                            viewerEmail = viewerEmail,
                            actions = ratingActions,
                            shown = SHOWN_RATINGS,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { BooksToolbar(shop.username, filters, shopActions) }
                    items(count = books.itemCount, key = books.itemKey { it.id }) { index ->
                        val book = books[index] ?: return@items
                        BoxWithConstraints {
                            BookCard(
                                book,
                                onClick = { onOpenBook(book.id) },
                                width = maxWidth,
                                inWishlist = book.id in wishlist,
                                onToggleWishlist = { shopActions.onToggleWishlist(book) },
                                inCart = book.id in cart,
                                onToggleCart = { shopActions.onToggleCart(book) },
                            )
                        }
                    }
                    if (books.loadState.refresh is LoadState.NotLoading && books.itemCount == 0) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val filtered = filters.search.isNotBlank() || filters.inStockOnly
                            Text(
                                stringResource(if (filtered) R.string.feature_shop_no_match else R.string.feature_shop_no_books),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopHeader(shop: SellerShop) {
    val locale = currentLocale()
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            shop.banner?.let {
                AsyncImage(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (shop.profilePicture != null) {
                    AsyncImage(
                        shop.profilePicture,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Outlined.Storefront, contentDescription = null)
                }
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(shop.username, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                RatingLine(shop.sellerRatingAverage, shop.sellerRatingCount)
                val since = formatMonthYear(shop.joinedAt, locale)
                if (since.isNotEmpty()) {
                    Text(
                        stringResource(R.string.feature_shop_since, since),
                        style = MaterialTheme.typography.bodySmall,
                        color = BookStoreTheme.colors.textMuted,
                    )
                }
            }
        }
        Text(
            text =
                stringResource(
                    R.string.feature_shop_stats,
                    formatCount(shop.books, locale),
                    formatCount(shop.inStock, locale),
                    formatCount(shop.sold, locale),
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = BookStoreTheme.colors.textMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
        // The reviews of the books this seller listed, beside how the seller is rated.
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.feature_shop_book_reviews),
                style = MaterialTheme.typography.bodyMedium,
                color = BookStoreTheme.colors.textMuted,
                modifier = Modifier.padding(end = 8.dp),
            )
            if (shop.ratingCount > 0) {
                RatingLine(shop.ratingAverage, shop.ratingCount)
            } else {
                Text(
                    stringResource(R.string.feature_shop_no_book_reviews),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BookStoreTheme.colors.textMuted,
                )
            }
        }
    }
}

/** "Books from …": the search box, the sort and "In stock only", as on the website. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BooksToolbar(
    username: String,
    filters: ShopFilters,
    actions: ShopActions,
) {
    var sorting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.feature_shop_books_from, username),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        OutlinedTextField(
            value = filters.search,
            onValueChange = actions.onSearch,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.feature_shop_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (filters.search.isNotEmpty()) {
                    IconButton(onClick = { actions.onSearch("") }) {
                        Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.feature_shop_clear_search))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                TextButton(onClick = { sorting = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(sortLabel(filters.sort)), modifier = Modifier.padding(start = 8.dp))
                }
                DropdownMenu(expanded = sorting, onDismissRequest = { sorting = false }) {
                    SHOP_SORTS.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(stringResource(sortLabel(sort))) },
                            onClick = {
                                sorting = false
                                actions.onSort(sort)
                            },
                            leadingIcon = if (sort == filters.sort) ({ Icon(Icons.Outlined.Check, contentDescription = null) }) else null,
                        )
                    }
                }
            }
            FilterChip(
                selected = filters.inStockOnly,
                onClick = { actions.onInStockOnly(!filters.inStockOnly) },
                label = { Text(stringResource(R.string.feature_shop_in_stock_only)) },
            )
        }
    }
}

/** The website's sorts for a shop. */
private val SHOP_SORTS =
    listOf(
        CatalogueSort.Relevant,
        CatalogueSort.Newest,
        CatalogueSort.Rated,
        CatalogueSort.PriceLowHigh,
        CatalogueSort.PriceHighLow,
        CatalogueSort.DealPercent,
    )

private fun sortLabel(sort: CatalogueSort): Int =
    when (sort) {
        CatalogueSort.Relevant -> R.string.feature_shop_sort_relevant
        CatalogueSort.Newest -> R.string.feature_shop_sort_newest
        CatalogueSort.Rated -> R.string.feature_shop_sort_rated
        CatalogueSort.PriceLowHigh -> R.string.feature_shop_sort_price_low
        CatalogueSort.PriceHighLow -> R.string.feature_shop_sort_price_high
        CatalogueSort.DealPercent, CatalogueSort.DealAmount -> R.string.feature_shop_sort_deals
    }

private const val SHOWN_RATINGS = 3
