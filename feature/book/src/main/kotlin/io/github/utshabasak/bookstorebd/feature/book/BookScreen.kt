package io.github.utshabasak.bookstorebd.feature.book

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.ReviewSummary
import io.github.utshabasak.bookstorebd.core.model.SellerSummary
import io.github.utshabasak.bookstorebd.core.ui.BookCard
import io.github.utshabasak.bookstorebd.core.ui.BookCover
import io.github.utshabasak.bookstorebd.core.ui.BookDetailSkeleton
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.ErrorState
import io.github.utshabasak.bookstorebd.core.ui.LoadingState
import io.github.utshabasak.bookstorebd.core.ui.PriceTag
import io.github.utshabasak.bookstorebd.core.ui.QuantityStepper
import io.github.utshabasak.bookstorebd.core.ui.RatingLine
import io.github.utshabasak.bookstorebd.core.ui.ReviewActions
import io.github.utshabasak.bookstorebd.core.ui.ReviewsSection
import io.github.utshabasak.bookstorebd.core.ui.ShopPerks
import io.github.utshabasak.bookstorebd.core.ui.SkeletonScreen
import io.github.utshabasak.bookstorebd.core.ui.WishlistButton
import io.github.utshabasak.bookstorebd.core.ui.conditionLabel
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CartRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CatalogueRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.ChatRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.MyBooksRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.ShopRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignInRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.WriteReviewRoute

@Composable
fun BookRouteScreen(
    route: BookRoute,
    navigator: Navigator,
    viewModel: BookViewModel = hiltViewModel<BookViewModel, BookViewModel.Factory>(creationCallback = { it.create(route.id) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val locale = currentLocale()
    val resources = LocalResources.current
    val texts =
        BookTexts(
            signIn = stringResource(R.string.feature_book_sign_in_needed),
            signInAction = stringResource(R.string.feature_book_sign_in),
            added = stringResource(R.string.feature_book_added),
            removed = stringResource(R.string.feature_book_removed),
            viewCart = stringResource(R.string.feature_book_view_cart),
            requested = stringResource(R.string.feature_book_requested),
            withdrawn = stringResource(R.string.feature_book_request_withdrawn),
            shareTitle = stringResource(R.string.feature_book_share),
        )
    var failure by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                BookEvent.SignInRequired -> {
                    if (snackbar.showSnackbar(texts.signIn, texts.signInAction) ==
                        SnackbarResult.ActionPerformed
                    ) {
                        navigator.navigate(SignInRoute)
                    }
                }

                is BookEvent.AddedToCart -> {
                    val added =
                        if (event.copies > 1) {
                            resources.getQuantityString(
                                R.plurals.feature_book_copies_added,
                                event.copies,
                                formatCount(event.copies, locale),
                            )
                        } else {
                            texts.added
                        }
                    if (snackbar.showSnackbar(added, texts.viewCart) == SnackbarResult.ActionPerformed) navigator.navigate(CartRoute)
                }

                BookEvent.RemovedFromCart -> {
                    snackbar.showSnackbar(texts.removed)
                }

                is BookEvent.RequestChanged -> {
                    snackbar.showSnackbar(if (event.requested) texts.requested else texts.withdrawn)
                }

                is BookEvent.Failed -> {
                    failure = event.error
                }

                is BookEvent.Message -> {
                    snackbar.showSnackbar(event.text)
                }
            }
        }
    }
    LaunchedEffect(Unit) { viewModel.refreshReviews() }
    ErrorSnackbarEffect(failure, snackbar) { failure = null }

    BookScreen(
        state = state,
        snackbar = snackbar,
        onBack = navigator::back,
        onRetry = viewModel::refresh,
        onQuantity = viewModel::setQuantity,
        onToggleCart = viewModel::toggleCart,
        onToggleWishlist = viewModel::toggleWishlist,
        onToggleRequest = viewModel::toggleRequest,
        onShare = { book ->
            val link = state.siteUrl?.let { "$it/book/${book.id}" } ?: return@BookScreen
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${book.title} — $link")
            context.startActivity(Intent.createChooser(send, texts.shareTitle))
        },
        onOpen = navigator::navigate,
        openAtReviews = route.reviews,
        reviewActions =
            ReviewActions(
                onWrite = {
                    val title = state.detail?.book?.title.orEmpty()
                    navigator.navigate(WriteReviewRoute("book", route.id, title))
                },
                onReport = viewModel::report,
                onReply = viewModel::reply,
                onDeleteReply = viewModel::deleteReply,
                onSignIn = { navigator.navigate(SignInRoute) },
            ),
    )
}

private data class BookTexts(
    val signIn: String,
    val signInAction: String,
    val added: String,
    val removed: String,
    val viewCart: String,
    val requested: String,
    val withdrawn: String,
    val shareTitle: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(
    state: BookUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onQuantity: (Int) -> Unit,
    onToggleCart: () -> Unit,
    onToggleWishlist: () -> Unit,
    onToggleRequest: () -> Unit,
    onShare: (Book) -> Unit,
    onOpen: (androidx.navigation3.runtime.NavKey) -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    reviewActions: ReviewActions = ReviewActions({}, { _, _ -> }, { _, _ -> }, {}),
    openAtReviews: Boolean = false,
) {
    val book = state.detail?.book
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_book_back))
                    }
                },
                actions = {
                    if (book != null) {
                        IconButton(onClick = { onShare(book) }) {
                            Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.feature_book_share))
                        }
                        WishlistButton(inWishlist = state.inWishlist, title = book.title, onToggle = onToggleWishlist)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            book == null && state.error != null -> {
                ErrorState(state.error, onRetry = onRetry, modifier = Modifier.padding(padding))
            }

            book == null -> {
                SkeletonScreen(Modifier.padding(padding)) { BookDetailSkeleton() }
            }

            else -> {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    val sections: LazyListScope.() -> Unit = {
                        bookSections(book, state, onQuantity, onToggleCart, onToggleRequest, onOpen, reviewActions)
                    }
                    val list = rememberLazyListState()
                    val wide = maxWidth >= TWO_COLUMNS
                    // A link to the reviews, such as a "delivered" notice, lands on them once the book is in.
                    // Items are keyed, so a seller row arriving later above them keeps them in view.
                    var landed by rememberSaveable { mutableStateOf(!openAtReviews) }
                    LaunchedEffect(landed) {
                        if (!landed) {
                            list.scrollToItem(reviewsIndex(book, state, withGallery = !wide))
                            landed = true
                        }
                    }
                    if (wide) {
                        // Wide screens: the pictures stay in view on the left while the details scroll on the right.
                        Row(Modifier.fillMaxSize()) {
                            Gallery(book, coverWidth = 280.dp, modifier = Modifier.weight(GALLERY_SHARE).fillMaxHeight())
                            LazyColumn(
                                state = list,
                                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                                modifier = Modifier.weight(1 - GALLERY_SHARE).fillMaxHeight(),
                                content = sections,
                            )
                        }
                    } else {
                        LazyColumn(state = list, contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
                            item(key = "gallery") { Gallery(book) }
                            sections()
                        }
                    }
                }
            }
        }
    }
}

/** Everything under the pictures: price and buying, the seller, details, description, reviews and related books. */
@Suppress("LongParameterList")
private fun LazyListScope.bookSections(
    book: Book,
    state: BookUiState,
    onQuantity: (Int) -> Unit,
    onToggleCart: () -> Unit,
    onToggleRequest: () -> Unit,
    onOpen: (NavKey) -> Unit,
    reviewActions: ReviewActions,
) {
    item(key = "summary") { Summary(book, state, onQuantity, onToggleCart, onToggleRequest, onOpen) }
    state.seller?.let { seller ->
        item(key = "seller") {
            SellerRow(
                seller = seller,
                onOpen = { onOpen(ShopRoute(seller.username)) },
                onMessage = messageSeller(state.viewerEmail, book.sellerEmail, seller.username, onOpen),
            )
        }
    }
    item(key = "details") { Details(book, onOpen) }
    if (book.description.isNotBlank()) item(key = "description") { Description(book.description) }
    item(key = REVIEWS) { ReviewsSection(state.reviews, ofSeller = false, viewerEmail = state.viewerEmail, actions = reviewActions) }
    val related = state.detail?.related.orEmpty()
    if (related.isNotEmpty()) {
        item(key = "related") {
            SectionTitle(stringResource(R.string.feature_book_related))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(related, key = { it.id }) { other -> BookCard(other, onClick = { onOpen(BookRoute(other.id)) }) }
            }
        }
    }
}

/** Where the reviews are in the page's list: after the pictures (on a phone), the summary, seller, details and description. */
private fun reviewsIndex(
    book: Book,
    state: BookUiState,
    withGallery: Boolean,
): Int =
    listOf(withGallery, true, state.seller != null, true, book.description.isNotBlank()).count { it }

private const val REVIEWS = "reviews"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Gallery(
    book: Book,
    modifier: Modifier = Modifier,
    coverWidth: Dp = 220.dp,
) {
    val pictures = book.images.ifEmpty { listOf(null) }
    val pager = rememberPagerState { pictures.size }
    val locale = currentLocale()
    val position =
        stringResource(R.string.feature_book_picture_of, formatCount(pager.currentPage + 1, locale), formatCount(pictures.size, locale))
    val described = stringResource(R.string.feature_book_cover_of, book.title) + ", " + position
    Box(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.semantics { contentDescription = described },
        ) { page ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                BookCover(url = pictures[page], drawnWidth = coverWidth, modifier = Modifier.width(coverWidth))
            }
        }
        if (pictures.size > 1) {
            Text(
                text = position,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.inverseSurface)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Summary(
    book: Book,
    state: BookUiState,
    onQuantity: (Int) -> Unit,
    onToggleCart: () -> Unit,
    onToggleRequest: () -> Unit,
    onOpen: (androidx.navigation3.runtime.NavKey) -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(book.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        if (book.author.isNotBlank()) {
            Text(
                text = stringResource(R.string.feature_book_by, book.author),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.feature_book_more_by_author)) {
                            onOpen(CatalogueRoute(search = book.author))
                        }.padding(vertical = 12.dp),
            )
        }
        PriceTag(book, large = true)
        RatingLine(book.ratingAverage, book.ratingCount)
        Demand(book)
        Availability(book)
        Spacer(Modifier.height(4.dp))
        BuyActions(book, state, onQuantity, onToggleCart, onToggleRequest, onManage = { onOpen(MyBooksRoute) })
        ShopPerks(returnDays = state.returnDays, delivery = state.delivery, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun BuyActions(
    book: Book,
    state: BookUiState,
    onQuantity: (Int) -> Unit,
    onToggleCart: () -> Unit,
    onToggleRequest: () -> Unit,
    onManage: () -> Unit,
) {
    val inCart = state.cartQuantity != null
    val own = state.viewerEmail != null && state.viewerEmail.equals(book.sellerEmail, ignoreCase = true)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (own) {
            // A seller cannot buy their own book, and the shop refuses it too: they manage it instead.
            Button(onClick = onManage, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Outlined.Storefront, contentDescription = null)
                Text(stringResource(R.string.feature_book_manage_listing), Modifier.padding(start = 8.dp))
            }
        } else if (book.inStock) {
            // Before the book is in the cart, how many to add; after, how many the cart holds.
            if (book.stock > 1) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(if (inCart) R.string.feature_book_in_your_cart else R.string.feature_book_quantity),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    QuantityStepper(
                        value = (state.cartQuantity ?: state.quantity).coerceIn(1, book.stock),
                        max = book.stock,
                        onChange = onQuantity,
                    )
                }
            }
            if (inCart) {
                OutlinedButton(
                    onClick = onToggleCart,
                    enabled = !state.adding,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Outlined.RemoveShoppingCart, contentDescription = null)
                    Text(stringResource(R.string.feature_book_remove_from_cart), Modifier.padding(start = 8.dp))
                }
            } else {
                Button(onClick = onToggleCart, enabled = !state.adding, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    if (state.adding) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.AddShoppingCart, contentDescription = null)
                        Text(stringResource(R.string.feature_book_add_to_cart), Modifier.padding(start = 8.dp))
                    }
                }
            }
        } else {
            OutlinedButton(onClick = onToggleRequest, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                val requested = state.requested == true
                Icon(if (requested) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsNone, contentDescription = null)
                Text(
                    stringResource(if (requested) R.string.feature_book_cancel_request else R.string.feature_book_notify_me),
                    Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** How wanted it is, as on the website: views and wishlists. */
@Composable
private fun Demand(book: Book) {
    val locale = currentLocale()
    val views = pluralStringResource(R.plurals.feature_book_views, book.viewCount, formatCount(book.viewCount, locale))
    val wishlists = pluralStringResource(R.plurals.feature_book_wishlists, book.wishlistCount, formatCount(book.wishlistCount, locale))
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        DemandItem(Icons.Outlined.Visibility, views)
        DemandItem(Icons.Outlined.FavoriteBorder, wishlists)
    }
}

@Composable
private fun DemandItem(
    icon: ImageVector,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = BookStoreTheme.colors.textMuted)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = BookStoreTheme.colors.textMuted,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun Availability(book: Book) {
    val locale = currentLocale()
    val availability =
        when {
            !book.inStock -> stringResource(R.string.feature_book_sold_out)
            book.stock <= LOW_STOCK -> pluralStringResource(R.plurals.feature_book_only_left, book.stock, formatCount(book.stock, locale))
            else -> stringResource(R.string.feature_book_in_stock)
        }
    Text(
        availability,
        style = MaterialTheme.typography.titleSmall,
        color = if (book.inStock) BookStoreTheme.colors.success else MaterialTheme.colorScheme.error,
    )
    if (book.type == BookType.Old) {
        // Only what the seller gave: a second-hand copy with no condition stated says just that.
        Text(
            book.condition?.let { stringResource(R.string.feature_book_second_hand, conditionLabel(it)) }
                ?: stringResource(R.string.feature_book_second_hand_plain),
            style = MaterialTheme.typography.bodyMedium,
        )
        book.conditionDetails?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = BookStoreTheme.colors.textMuted) }
    }
}

@Composable
private fun SellerRow(
    seller: SellerSummary,
    onOpen: () -> Unit,
    onMessage: (() -> Unit)?,
) {
    HorizontalDivider()
    ListItem(
        headlineContent = { Text(seller.username) },
        overlineContent = { Text(stringResource(R.string.feature_book_sold_by)) },
        supportingContent =
            if (seller.ratingCount > 0) {
                { RatingLine(seller.ratingAverage, seller.ratingCount) }
            } else {
                null
            },
        leadingContent = {
            if (seller.picture != null) {
                AsyncImage(model = seller.picture, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape))
            } else {
                Icon(Icons.Outlined.Storefront, contentDescription = null)
            }
        },
        trailingContent = { TextButton(onClick = onOpen) { Text(stringResource(R.string.feature_book_visit_shop)) } },
        modifier = Modifier.clickable(role = Role.Button, onClick = onOpen),
    )
    if (onMessage != null) {
        // On the same surface as the seller's row above, so the two read as one.
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            OutlinedButton(onClick = onMessage, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.feature_book_message_seller), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    HorizontalDivider()
}

/**
 * "Message the seller": the conversation, or sign-in first. No button on
 * one's own listing, or when the seller is not known.
 */
private fun messageSeller(
    viewer: String?,
    seller: String,
    sellerName: String,
    onOpen: (NavKey) -> Unit,
): (() -> Unit)? =
    if (seller.isBlank() || viewer.equals(seller, ignoreCase = true)) {
        null
    } else {
        { onOpen(if (viewer == null) SignInRoute else ChatRoute(seller, sellerName)) }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(
    book: Book,
    onOpen: (androidx.navigation3.runtime.NavKey) -> Unit,
) {
    val locale = currentLocale()
    SectionTitle(stringResource(R.string.feature_book_details))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailLine(R.string.feature_book_publisher, book.publisher)
        DetailLine(R.string.feature_book_language, book.language)
        DetailLine(R.string.feature_book_isbn, book.isbn)
        book.pages?.let { DetailLine(R.string.feature_book_pages, formatCount(it, locale)) }
        if (book.categories.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                book.categories.forEach { category ->
                    AssistChip(onClick = { onOpen(CatalogueRoute(category = category)) }, label = { Text(category) })
                }
            }
        }
    }
}

@Composable
private fun DetailLine(
    label: Int,
    value: String,
) {
    if (value.isBlank()) return
    Row(Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = BookStoreTheme.colors.textMuted,
            modifier = Modifier.width(110.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Description(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionTitle(stringResource(R.string.feature_book_about))
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(stringResource(if (expanded) R.string.feature_book_show_less else R.string.feature_book_read_more))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier =
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
                .semantics { heading() },
    )
}

private const val LOW_STOCK = 5

/** From this width the pictures and the details sit side by side. */
private val TWO_COLUMNS = 840.dp
private const val GALLERY_SHARE = 0.42f
private const val COLLAPSED_LINES = 5
