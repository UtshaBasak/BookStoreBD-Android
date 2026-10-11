package io.github.utshabasak.bookstorebd.feature.catalogue

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.utshabasak.bookstorebd.core.data.repository.CatalogueLoadException
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.CatalogueQuery
import io.github.utshabasak.bookstorebd.core.model.CatalogueSort
import io.github.utshabasak.bookstorebd.core.model.CategoryGroup
import io.github.utshabasak.bookstorebd.core.model.Suggestions
import io.github.utshabasak.bookstorebd.core.ui.BookCard
import io.github.utshabasak.bookstorebd.core.ui.EmptyState
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.ErrorState
import io.github.utshabasak.bookstorebd.core.ui.LoadingState
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CatalogueRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.ShopRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignInRoute
import io.github.utshabasak.bookstorebd.core.ui.onHardwareEnter

@Composable
fun CatalogueRouteScreen(
    route: CatalogueRoute,
    navigator: Navigator,
    viewModel: CatalogueViewModel =
        hiltViewModel<CatalogueViewModel, CatalogueViewModel.Factory>(creationCallback = { factory -> factory.create(route) }),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val text by viewModel.searchText.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val recent by viewModel.recentSearches.collectAsStateWithLifecycle()
    val popular by viewModel.popularSearches.collectAsStateWithLifecycle()
    val groups by viewModel.categoryGroups.collectAsStateWithLifecycle()
    val wishlist by viewModel.wishlistIds.collectAsStateWithLifecycle()
    val results = viewModel.results.collectAsLazyPagingItems()
    val snackbar = remember { SnackbarHostState() }
    val signInPrompt = stringResource(R.string.feature_catalogue_sign_in_to_save)
    val signInAction = stringResource(R.string.feature_catalogue_sign_in)
    var failure by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                CatalogueEvent.SignInToSave -> {
                    if (snackbar.showSnackbar(signInPrompt, signInAction) == SnackbarResult.ActionPerformed) navigator.navigate(SignInRoute)
                }

                is CatalogueEvent.Failed -> {
                    failure = event.error
                }
            }
        }
    }
    ErrorSnackbarEffect(failure, snackbar) { failure = null }

    CatalogueScreen(
        query = query,
        searchText = text,
        suggestions = suggestions,
        recentSearches = recent,
        popularSearches = popular,
        categoryGroups = groups,
        wishlist = wishlist,
        results = results,
        focusSearchOnStart = route.focusSearch,
        snackbar = snackbar,
        actions =
            CatalogueActions(
                onSearchTextChange = viewModel::onSearchTextChange,
                onSearch = viewModel::search,
                onAuthor = viewModel::searchAuthor,
                onCategory = viewModel::filterByCategory,
                onSeller = { navigator.navigate(ShopRoute(it)) },
                onOpenBook = { navigator.navigate(BookRoute(it)) },
                onForgetSearch = viewModel::forgetSearch,
                onApplyFilters = viewModel::applyFilters,
                onClearFilters = viewModel::clearFilters,
                onSort = viewModel::setSort,
                onToggleWishlist = viewModel::toggleWishlist,
            ),
    )
}

/** Everything the catalogue screen can ask for, in one place. */
data class CatalogueActions(
    val onSearchTextChange: (String) -> Unit,
    val onSearch: (String) -> Unit,
    val onAuthor: (String) -> Unit,
    val onCategory: (String) -> Unit,
    val onSeller: (String) -> Unit,
    val onOpenBook: (String) -> Unit,
    val onForgetSearch: (String) -> Unit,
    val onApplyFilters: (CatalogueQuery) -> Unit,
    val onClearFilters: () -> Unit,
    val onSort: (CatalogueSort) -> Unit,
    val onToggleWishlist: (Book) -> Unit,
)

@Composable
fun CatalogueScreen(
    query: CatalogueQuery,
    searchText: String,
    suggestions: Suggestions?,
    recentSearches: List<String>,
    popularSearches: List<String>,
    categoryGroups: List<CategoryGroup>,
    wishlist: Set<String>,
    results: LazyPagingItems<Book>,
    actions: CatalogueActions,
    modifier: Modifier = Modifier,
    focusSearchOnStart: Boolean = false,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val submit = { text: String ->
        actions.onSearch(text)
        focus.clearFocus()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            SearchField(
                text = searchText,
                onTextChange = actions.onSearchTextChange,
                onSubmit = submit,
                onFocusChange = { searching = it },
                focusOnStart = focusSearchOnStart,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (searching) {
                SuggestionPanel(
                    text = searchText,
                    suggestions = suggestions,
                    recent = recentSearches,
                    popular = popularSearches,
                    onSearch = submit,
                    onForget = actions.onForgetSearch,
                    onBook = {
                        focus.clearFocus()
                        actions.onOpenBook(it)
                    },
                    onAuthor = {
                        focus.clearFocus()
                        actions.onAuthor(it)
                    },
                    onCategory = {
                        focus.clearFocus()
                        actions.onCategory(it)
                    },
                    onSeller = {
                        focus.clearFocus()
                        actions.onSeller(it)
                    },
                )
            } else {
                ResultsToolbar(query = query, onFilters = { showFilters = true }, onSort = actions.onSort)
                Results(
                    results = results,
                    wishlist = wishlist,
                    onOpenBook = actions.onOpenBook,
                    onToggleWishlist = actions.onToggleWishlist,
                    onClearFilters = actions.onClearFilters,
                    hasFilters = query.activeFilterCount > 0,
                )
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            current = query,
            groups = categoryGroups,
            onApply = {
                actions.onApplyFilters(it)
                showFilters = false
            },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun SearchField(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    focusOnStart: Boolean,
) {
    val requester = remember { FocusRequester() }
    val context = LocalContext.current
    val locale = currentLocale()
    val voiceAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val voice =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (result.resultCode == Activity.RESULT_OK && !heard.isNullOrBlank()) {
                onTextChange(heard)
                onSubmit(heard)
            }
        }
    val prompt = stringResource(R.string.feature_catalogue_voice_prompt)
    LaunchedEffect(focusOnStart) { if (focusOnStart) requester.requestFocus() }

    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.feature_catalogue_search_hint)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            Row {
                if (text.isNotEmpty()) {
                    IconButton(onClick = { onTextChange("") }) {
                        Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.feature_catalogue_clear_search))
                    }
                }
                if (voiceAvailable) {
                    IconButton(onClick = {
                        // Bangla or English, whichever the app is in; the server matches across both.
                        val language = if (locale.language == "bn") "bn-BD" else "en-US"
                        voice.launch(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                                .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt),
                        )
                    }) {
                        Icon(Icons.Outlined.Mic, contentDescription = stringResource(R.string.feature_catalogue_voice_search))
                    }
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit(text) }),
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .onHardwareEnter { onSubmit(text) }
                .focusRequester(requester)
                .onFocusChanged { onFocusChange(it.isFocused) },
    )
}

@Composable
private fun ResultsToolbar(
    query: CatalogueQuery,
    onFilters: () -> Unit,
    onSort: (CatalogueSort) -> Unit,
) {
    var sortOpen by remember { mutableStateOf(false) }
    val locale = currentLocale()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onFilters) {
            BadgedBox(badge = {
                if (query.activeFilterCount > 0) Badge { Text(formatCount(query.activeFilterCount, locale)) }
            }) {
                Icon(Icons.Outlined.FilterList, contentDescription = null)
            }
            Text(stringResource(R.string.feature_catalogue_filters), Modifier.padding(start = 8.dp))
        }
        Row {
            TextButton(onClick = { sortOpen = true }) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null)
                Text(sortLabel(query.sort), Modifier.padding(start = 8.dp))
            }
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                CatalogueSort.entries.forEach { sort ->
                    DropdownMenuItem(text = { Text(sortLabel(sort)) }, onClick = {
                        sortOpen = false
                        onSort(sort)
                    })
                }
            }
        }
    }
}

@Composable
private fun Results(
    results: LazyPagingItems<Book>,
    wishlist: Set<String>,
    onOpenBook: (String) -> Unit,
    onToggleWishlist: (Book) -> Unit,
    onClearFilters: () -> Unit,
    hasFilters: Boolean,
) {
    val refresh = results.loadState.refresh
    when {
        refresh is LoadState.Loading && results.itemCount == 0 -> {
            LoadingState(message = stringResource(R.string.feature_catalogue_searching))
        }

        refresh is LoadState.Error && results.itemCount == 0 -> {
            ErrorState(refresh.appError(), onRetry = results::retry)
        }

        refresh is LoadState.NotLoading && results.itemCount == 0 -> {
            EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.feature_catalogue_empty_title),
                body = stringResource(R.string.feature_catalogue_empty_body),
                actionLabel = if (hasFilters) stringResource(R.string.feature_catalogue_clear_filters) else null,
                onAction = if (hasFilters) onClearFilters else null,
            )
        }

        else -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(count = results.itemCount, key = results.itemKey { it.id }) { index ->
                    val book = results[index] ?: return@items
                    BoxWithConstraints {
                        BookCard(
                            book = book,
                            onClick = { onOpenBook(book.id) },
                            width = maxWidth,
                            inWishlist = book.id in wishlist,
                            onToggleWishlist = { onToggleWishlist(book) },
                        )
                    }
                }
                when (val append = results.loadState.append) {
                    is LoadState.Loading -> {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    is LoadState.Error -> {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ErrorState(append.appError(), onRetry = results::retry)
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}

private fun LoadState.Error.appError(): AppError = (error as? CatalogueLoadException)?.error ?: AppError.Unexpected(error)

@Composable
internal fun sortLabel(sort: CatalogueSort): String =
    stringResource(
        when (sort) {
            CatalogueSort.Relevant -> R.string.feature_catalogue_sort_relevant
            CatalogueSort.Newest -> R.string.feature_catalogue_sort_newest
            CatalogueSort.Rated -> R.string.feature_catalogue_sort_rated
            CatalogueSort.PriceLowHigh -> R.string.feature_catalogue_sort_price_low
            CatalogueSort.PriceHighLow -> R.string.feature_catalogue_sort_price_high
            CatalogueSort.DealPercent -> R.string.feature_catalogue_sort_deal_percent
            CatalogueSort.DealAmount -> R.string.feature_catalogue_sort_deal_amount
        },
    )
