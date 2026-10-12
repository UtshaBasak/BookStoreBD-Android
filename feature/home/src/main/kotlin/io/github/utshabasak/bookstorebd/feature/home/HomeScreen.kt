package io.github.utshabasak.bookstorebd.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.CatalogueSort
import io.github.utshabasak.bookstorebd.core.ui.BookCard
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.ErrorState
import io.github.utshabasak.bookstorebd.core.ui.LoadingState
import io.github.utshabasak.bookstorebd.core.ui.ShelfSkeleton
import io.github.utshabasak.bookstorebd.core.ui.SkeletonBlock
import io.github.utshabasak.bookstorebd.core.ui.SkeletonScreen
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.errorMessage
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CatalogueRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.MessagesRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.NotificationsRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignInRoute

@Composable
fun HomeRouteScreen(
    navigator: Navigator,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val signInPrompt = stringResource(R.string.feature_home_sign_in_to_save)
    val signInAction = stringResource(R.string.feature_home_sign_in)
    val signInForCart = stringResource(R.string.feature_home_sign_in_for_cart)
    var failure by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                HomeEvent.SignInToSave -> {
                    if (snackbar.showSnackbar(signInPrompt, signInAction) == SnackbarResult.ActionPerformed) navigator.navigate(SignInRoute)
                }

                HomeEvent.SignInForCart -> {
                    if (snackbar.showSnackbar(signInForCart, signInAction) ==
                        SnackbarResult.ActionPerformed
                    ) {
                        navigator.navigate(SignInRoute)
                    }
                }

                is HomeEvent.Failed -> {
                    failure = event.error
                }
            }
        }
    }
    ErrorSnackbarEffect(failure, snackbar) { failure = null }
    val inbox by viewModel.inbox.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        snackbar = snackbar,
        inbox = inbox,
        onInbox = navigator::navigate,
        onRefresh = viewModel::refresh,
        onSearch = { navigator.navigate(CatalogueRoute(focusSearch = true)) },
        onOpenBook = { navigator.navigate(BookRoute(it.id)) },
        onToggleWishlist = viewModel::toggleWishlist,
        onToggleCart = viewModel::toggleCart,
        onOpenCatalogue = navigator::navigate,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onOpenBook: (Book) -> Unit,
    onToggleWishlist: (Book) -> Unit,
    onOpenCatalogue: (CatalogueRoute) -> Unit,
    modifier: Modifier = Modifier,
    inbox: InboxBadges = InboxBadges(),
    onInbox: (NavKey) -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onToggleCart: (Book) -> Unit = {},
) {
    val cards = CardActions(onOpenBook, onToggleWishlist, onToggleCart)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.feature_home_title),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = { if (inbox.signedIn) InboxActions(inbox, onInbox) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val sections = state.sections
        when {
            sections == null && state.error != null -> {
                ErrorState(state.error, onRetry = onRefresh, modifier = Modifier.padding(padding))
            }

            sections == null -> {
                SkeletonScreen(Modifier.padding(padding), stringResource(R.string.feature_home_loading)) {
                    Column {
                        SkeletonBlock(Modifier.fillMaxWidth().padding(16.dp).height(56.dp), RoundedCornerShape(28.dp))
                        repeat(3) { ShelfSkeleton() }
                    }
                }
            }

            else -> {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                ) {
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        item { SearchEntry(onSearch) }
                        if (state.error != null) {
                            item { StaleNotice(errorMessage(state.error)) }
                        }
                        shelf(R.string.feature_home_deals, sections.deals, state, cards) {
                            onOpenCatalogue(CatalogueRoute(dealsOnly = true, sort = CatalogueSort.DealPercent))
                        }
                        shelf(R.string.feature_home_latest, state.latest, state, cards) {
                            onOpenCatalogue(CatalogueRoute(sort = CatalogueSort.Newest))
                        }
                        shelf(R.string.feature_home_recently_viewed, state.recentlyViewed, state, cards, null)
                        shelf(R.string.feature_home_trending, sections.trending, state, cards, null)
                        state.topPicks?.let { picks ->
                            shelf(
                                title = R.string.feature_home_top_picks,
                                books = picks.books,
                                state = state,
                                cards = cards,
                                subtitle = picksNote(picks.personal),
                            )
                        }
                        if (sections.categories.isNotEmpty()) {
                            item {
                                SectionTitle(stringResource(R.string.feature_home_categories))
                                ChipRow(sections.categories.map { it.name to it.books }) { onOpenCatalogue(CatalogueRoute(category = it)) }
                            }
                        }
                        shelf(R.string.feature_home_popular, sections.popular, state, cards, null)
                        shelf(R.string.feature_home_bestsellers, sections.bestsellers, state, cards, null)
                        if (sections.writers.isNotEmpty()) {
                            item {
                                SectionTitle(stringResource(R.string.feature_home_writers))
                                ChipRow(sections.writers.map { it.name to it.books }) { onOpenCatalogue(CatalogueRoute(search = it)) }
                            }
                        }
                        shelf(R.string.feature_home_top_rated, sections.topRated, state, cards) {
                            onOpenCatalogue(CatalogueRoute(sort = CatalogueSort.Rated))
                        }
                        shelf(R.string.feature_home_budget, sections.budget, state, cards, null)
                        shelf(R.string.feature_home_discover, sections.discover, state, cards) {
                            onOpenCatalogue(CatalogueRoute(sort = CatalogueSort.Newest))
                        }
                    }
                }
            }
        }
    }
}

/** What a card on a shelf can do: open the book, save it, and put it in the cart. */
private class CardActions(
    val open: (Book) -> Unit,
    val wishlist: (Book) -> Unit,
    val cart: (Book) -> Unit,
)

private fun androidx.compose.foundation.lazy.LazyListScope.shelf(
    title: Int,
    books: List<Book>,
    state: HomeUiState,
    cards: CardActions,
    subtitle: Int? = null,
    onSeeAll: (() -> Unit)? = null,
) {
    if (books.isEmpty()) return
    item(key = title) {
        SectionTitle(stringResource(title), onSeeAll)
        if (subtitle != null) {
            Text(
                stringResource(subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = BookStoreTheme.colors.textMuted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(books, key = { it.id }) { book ->
                BookCard(
                    book = book,
                    onClick = { cards.open(book) },
                    inWishlist = book.id in state.wishlist,
                    onToggleWishlist = { cards.wishlist(book) },
                    inCart = book.id in state.cart,
                    onToggleCart = { cards.cart(book) },
                )
            }
        }
    }
}

@Composable
private fun SearchEntry(onSearch: () -> Unit) {
    val label = stringResource(R.string.feature_home_search_hint)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(min = 52.dp)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onSearch),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = BookStoreTheme.colors.textMuted,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun StaleNotice(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun SectionTitle(
    title: String,
    onSeeAll: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { heading() },
        )
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) { Text(stringResource(R.string.feature_home_see_all)) }
        }
    }
}

@Composable
private fun ChipRow(
    items: List<Pair<String, Int>>,
    onClick: (String) -> Unit,
) {
    val locale = currentLocale()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.first }) { (name, count) ->
            AssistChip(onClick = { onClick(name) }, label = { Text("$name · ${formatCount(count, locale)}") })
        }
    }
}

/** Says where the picks come from: the person's own history, or the best-rated books while there is none. */
private fun picksNote(personal: Boolean): Int =
    if (personal) R.string.feature_home_top_picks_personal else R.string.feature_home_top_picks_general

/** Messages and the bell, each with its count, as in the website's header. */
@Composable
private fun InboxActions(
    inbox: InboxBadges,
    onInbox: (NavKey) -> Unit,
) {
    val locale = currentLocale()
    BadgedIcon(
        icon = Icons.AutoMirrored.Outlined.Chat,
        count = inbox.messages,
        label = pluralStringResource(R.plurals.feature_home_messages, inbox.messages, formatCount(inbox.messages, locale)),
        onClick = { onInbox(MessagesRoute) },
    )
    BadgedIcon(
        icon = Icons.Outlined.Notifications,
        count = inbox.notifications,
        label = pluralStringResource(R.plurals.feature_home_notifications, inbox.notifications, formatCount(inbox.notifications, locale)),
        onClick = { onInbox(NotificationsRoute) },
    )
}

@Composable
private fun BadgedIcon(
    icon: ImageVector,
    count: Int,
    label: String,
    onClick: () -> Unit,
) {
    val locale = currentLocale()
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        BadgedBox(badge = { if (count > 0) Badge { Text(if (count > MAX_BADGE) "$MAX_BADGE+" else formatCount(count, locale)) } }) {
            Icon(icon, contentDescription = null)
        }
    }
}

private const val MAX_BADGE = 99
