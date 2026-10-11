package io.github.utshabasak.bookstorebd.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.CatalogueSort
import io.github.utshabasak.bookstorebd.core.ui.BookCard
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.ErrorState
import io.github.utshabasak.bookstorebd.core.ui.LoadingState
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.errorMessage
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CatalogueRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
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
    var failure by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                HomeEvent.SignInToSave -> {
                    if (snackbar.showSnackbar(signInPrompt, signInAction) == SnackbarResult.ActionPerformed) navigator.navigate(SignInRoute)
                }

                is HomeEvent.Failed -> {
                    failure = event.error
                }
            }
        }
    }
    ErrorSnackbarEffect(failure, snackbar) { failure = null }
    HomeScreen(
        state = state,
        snackbar = snackbar,
        onRefresh = viewModel::refresh,
        onSearch = { navigator.navigate(CatalogueRoute(focusSearch = true)) },
        onOpenBook = { navigator.navigate(BookRoute(it.id)) },
        onToggleWishlist = viewModel::toggleWishlist,
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
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
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
                LoadingState(Modifier.padding(padding), stringResource(R.string.feature_home_loading))
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
                        shelf(R.string.feature_home_deals, sections.deals, state, onOpenBook, onToggleWishlist) {
                            onOpenCatalogue(CatalogueRoute(dealsOnly = true, sort = CatalogueSort.DealPercent))
                        }
                        shelf(R.string.feature_home_recently_viewed, state.recentlyViewed, state, onOpenBook, onToggleWishlist, null)
                        shelf(R.string.feature_home_trending, sections.trending, state, onOpenBook, onToggleWishlist, null)
                        if (sections.categories.isNotEmpty()) {
                            item {
                                SectionTitle(stringResource(R.string.feature_home_categories))
                                ChipRow(sections.categories.map { it.name to it.books }) { onOpenCatalogue(CatalogueRoute(category = it)) }
                            }
                        }
                        shelf(R.string.feature_home_popular, sections.popular, state, onOpenBook, onToggleWishlist, null)
                        shelf(R.string.feature_home_bestsellers, sections.bestsellers, state, onOpenBook, onToggleWishlist, null)
                        if (sections.writers.isNotEmpty()) {
                            item {
                                SectionTitle(stringResource(R.string.feature_home_writers))
                                ChipRow(sections.writers.map { it.name to it.books }) { onOpenCatalogue(CatalogueRoute(search = it)) }
                            }
                        }
                        shelf(R.string.feature_home_top_rated, sections.topRated, state, onOpenBook, onToggleWishlist) {
                            onOpenCatalogue(CatalogueRoute(sort = CatalogueSort.Rated))
                        }
                        shelf(R.string.feature_home_budget, sections.budget, state, onOpenBook, onToggleWishlist, null)
                        shelf(R.string.feature_home_discover, sections.discover, state, onOpenBook, onToggleWishlist) {
                            onOpenCatalogue(CatalogueRoute(sort = CatalogueSort.Newest))
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.shelf(
    title: Int,
    books: List<Book>,
    state: HomeUiState,
    onOpenBook: (Book) -> Unit,
    onToggleWishlist: (Book) -> Unit,
    onSeeAll: (() -> Unit)?,
) {
    if (books.isEmpty()) return
    item(key = title) {
        SectionTitle(stringResource(title), onSeeAll)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(books, key = { it.id }) { book ->
                BookCard(
                    book = book,
                    onClick = { onOpenBook(book) },
                    inWishlist = book.id in state.wishlist,
                    onToggleWishlist = { onToggleWishlist(book) },
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
