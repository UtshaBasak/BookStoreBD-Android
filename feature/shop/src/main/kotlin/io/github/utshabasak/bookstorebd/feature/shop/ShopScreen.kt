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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import io.github.utshabasak.bookstorebd.core.data.repository.CatalogueRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CommunityRepository
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
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.ShopRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.WriteReviewRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ShopMessage {
    data class Text(
        val text: String,
    ) : ShopMessage

    data class Failed(
        val error: AppError,
    ) : ShopMessage
}

/** A seller's shop front: who they are, how they are rated, and their books, newest first. */
@HiltViewModel(assistedFactory = ShopViewModel.Factory::class)
class ShopViewModel
    @AssistedInject
    constructor(
        @Assisted private val username: String,
        private val catalogue: CatalogueRepository,
        private val community: CommunityRepository,
        session: UserSession,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(username: String): ShopViewModel
        }

        val shop: StateFlow<SellerShop?> = catalogue.shop(username).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

        private val mutableError = MutableStateFlow<AppError?>(null)
        val error: StateFlow<AppError?> = mutableError.asStateFlow()

        val books: Flow<PagingData<Book>> =
            catalogue.search(CatalogueQuery(seller = username, sort = CatalogueSort.Newest)).cachedIn(viewModelScope)

        private val mutableRatings = MutableStateFlow<ReviewPage?>(null)
        val ratings: StateFlow<ReviewPage?> = mutableRatings.asStateFlow()

        val viewerEmail: StateFlow<String?> = session.user.map { it?.email }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private val messages = Channel<ShopMessage>(Channel.BUFFERED)
        val messageFlow: Flow<ShopMessage> = messages.receiveAsFlow()

        init {
            refresh()
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
        }
    }

fun EntryProviderScope<NavKey>.shopEntry(navigator: Navigator) {
    entry<ShopRoute> { route ->
        val viewModel = hiltViewModel<ShopViewModel, ShopViewModel.Factory>(creationCallback = { it.create(route.username) })
        val shop by viewModel.shop.collectAsStateWithLifecycle()
        val error by viewModel.error.collectAsStateWithLifecycle()
        val ratings by viewModel.ratings.collectAsStateWithLifecycle()
        val viewer by viewModel.viewerEmail.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { viewModel.refreshRatings() }
        var failure by remember { mutableStateOf<AppError?>(null) }
        LaunchedEffect(viewModel) {
            viewModel.messageFlow.collect { message ->
                when (message) {
                    is ShopMessage.Text -> snackbar.showSnackbar(message.text)
                    is ShopMessage.Failed -> failure = message.error
                }
            }
        }
        ErrorSnackbarEffect(failure, snackbar) { failure = null }
        ShopScreen(
            username = route.username,
            shop = shop,
            error = error,
            books = viewModel.books.collectAsLazyPagingItems(),
            onBack = navigator::back,
            onRetry = viewModel::refresh,
            onOpenBook = { navigator.navigate(BookRoute(it)) },
            ratings = ratings,
            viewerEmail = viewer,
            snackbar = snackbar,
            ratingActions =
                ReviewActions(
                    onWrite = { shop?.let { navigator.navigate(WriteReviewRoute("seller", it.id, it.username)) } },
                    onReport = viewModel::report,
                    onReply = viewModel::reply,
                    onDeleteReply = viewModel::deleteReply,
                ),
        )
    }
}

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
) {
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
                    items(count = books.itemCount, key = books.itemKey { it.id }) { index ->
                        val book = books[index] ?: return@items
                        BoxWithConstraints { BookCard(book, onClick = { onOpenBook(book.id) }, width = maxWidth) }
                    }
                    if (books.loadState.refresh is LoadState.NotLoading && books.itemCount == 0) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(stringResource(R.string.feature_shop_no_books), style = MaterialTheme.typography.bodyMedium)
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
    }
}

private const val SHOWN_RATINGS = 3
