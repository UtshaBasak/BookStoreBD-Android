package io.github.utshabasak.bookstorebd.feature.catalogue

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import io.github.utshabasak.bookstorebd.core.data.repository.CatalogueLoadException
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.CatalogueQuery
import io.github.utshabasak.bookstorebd.core.model.CatalogueSort
import io.github.utshabasak.bookstorebd.core.model.CategoryGroup
import io.github.utshabasak.bookstorebd.core.model.SellerHit
import io.github.utshabasak.bookstorebd.core.model.Suggestions
import io.github.utshabasak.bookstorebd.core.scanner.IsbnScanner
import io.github.utshabasak.bookstorebd.core.scanner.ScanBarcodeButton
import io.github.utshabasak.bookstorebd.core.ui.BookCard
import io.github.utshabasak.bookstorebd.core.ui.BookGridSkeleton
import io.github.utshabasak.bookstorebd.core.ui.EmptyState
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.ErrorState
import io.github.utshabasak.bookstorebd.core.ui.LoadingState
import io.github.utshabasak.bookstorebd.core.ui.SkeletonScreen
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CatalogueRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.ShopRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignInRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.WantedRoute
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
    val cart by viewModel.cartIds.collectAsStateWithLifecycle()
    val total by viewModel.total.collectAsStateWithLifecycle()
    val sellers by viewModel.sellers.collectAsStateWithLifecycle()
    val voiceLanguage by viewModel.voiceLanguage.collectAsStateWithLifecycle()
    val signInForCart = stringResource(R.string.feature_catalogue_sign_in_for_cart)
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

                CatalogueEvent.SignInForCart -> {
                    if (snackbar.showSnackbar(signInForCart, signInAction) ==
                        SnackbarResult.ActionPerformed
                    ) {
                        navigator.navigate(SignInRoute)
                    }
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
        cart = cart,
        results = results,
        total = total,
        sellers = sellers,
        voiceLanguage = voiceLanguage,
        focusSearchOnStart = route.focusSearch,
        scanOnStart = route.scan,
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
                onToggleCart = viewModel::toggleCart,
                onAskWanted = { navigator.navigate(WantedRoute(ask = it.ifBlank { null })) },
                onClearRecent = viewModel::clearRecentSearches,
                onVoiceLanguage = viewModel::setVoiceLanguage,
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
    val onAskWanted: (String) -> Unit = {},
    val onToggleCart: (Book) -> Unit = {},
    val onClearRecent: () -> Unit = {},
    val onVoiceLanguage: (String) -> Unit = {},
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
    scanOnStart: Boolean = false,
    cart: Set<String> = emptySet(),
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    total: Int? = null,
    sellers: List<SellerHit> = emptyList(),
    voiceLanguage: String? = null,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var scanning by rememberSaveable { mutableStateOf(scanOnStart) }
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
                onScan = { scanning = true },
                focusOnStart = focusSearchOnStart,
                voiceLanguage = voiceLanguage,
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
                    onClearRecent = actions.onClearRecent,
                    voice = VoiceChoice(voiceLanguage ?: defaultVoiceLanguage(currentLocale()), actions.onVoiceLanguage),
                )
            } else {
                ResultsToolbar(query = query, onFilters = { showFilters = true }, onSort = actions.onSort)
                Results(
                    results = results,
                    wishlist = wishlist,
                    cart = cart,
                    onOpenBook = actions.onOpenBook,
                    onToggleWishlist = actions.onToggleWishlist,
                    onToggleCart = actions.onToggleCart,
                    onClearFilters = actions.onClearFilters,
                    hasFilters = query.activeFilterCount > 0,
                    onAskWanted = { actions.onAskWanted(query.search) },
                    header = { ResultsHeader(query.search, total, sellers, actions.onSeller) },
                )
            }
        }
    }

    if (scanning) {
        IsbnScanner(
            onScanned = { isbn ->
                // The website's search matches ISBNs, so a scanned book is searched for like typed words.
                scanning = false
                actions.onSearchTextChange(isbn)
                submit(isbn)
            },
            onDismiss = { scanning = false },
        )
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
    onScan: () -> Unit,
    focusOnStart: Boolean,
    voiceLanguage: String?,
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
                // Scanning fills the box, so it is offered while the box is empty.
                if (text.isEmpty()) ScanBarcodeButton(onClick = onScan)
                if (voiceAvailable) {
                    IconButton(onClick = {
                        // The language chosen in the search panel, or else the app's own; the server matches across both.
                        val language = voiceLanguage ?: defaultVoiceLanguage(locale)
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
    cart: Set<String>,
    onOpenBook: (String) -> Unit,
    onToggleWishlist: (Book) -> Unit,
    onToggleCart: (Book) -> Unit,
    onClearFilters: () -> Unit,
    hasFilters: Boolean,
    onAskWanted: () -> Unit,
    header: @Composable () -> Unit = {},
) {
    val refresh = results.loadState.refresh
    when {
        refresh is LoadState.Loading && results.itemCount == 0 -> {
            SkeletonScreen(message = stringResource(R.string.feature_catalogue_searching)) { BookGridSkeleton() }
        }

        refresh is LoadState.Error && results.itemCount == 0 -> {
            ErrorState(refresh.appError(), onRetry = results::retry)
        }

        refresh is LoadState.NotLoading && results.itemCount == 0 -> {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { header() }
            EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.feature_catalogue_empty_title),
                body = stringResource(R.string.feature_catalogue_empty_body),
                // Fewer filters may find it; with none left, the Wanted board can.
                actionLabel =
                    stringResource(
                        if (hasFilters) R.string.feature_catalogue_clear_filters else R.string.feature_catalogue_ask_wanted,
                    ),
                onAction = if (hasFilters) onClearFilters else onAskWanted,
            )
        }

        else -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "header") { header() }
                items(count = results.itemCount, key = results.itemKey { it.id }) { index ->
                    val book = results[index] ?: return@items
                    BoxWithConstraints {
                        BookCard(
                            book = book,
                            onClick = { onOpenBook(book.id) },
                            width = maxWidth,
                            inWishlist = book.id in wishlist,
                            onToggleWishlist = { onToggleWishlist(book) },
                            inCart = book.id in cart,
                            onToggleCart = { onToggleCart(book) },
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

/** Bangla or English, whichever the app is in, until one is chosen. */
internal fun defaultVoiceLanguage(locale: java.util.Locale): String = if (locale.language == "bn") VOICE_BANGLA else VOICE_ENGLISH

internal const val VOICE_ENGLISH = "en-US"
internal const val VOICE_BANGLA = "bn-BD"

/** "Results for X" and how many books match, then the sellers whose name matches, as above the website's results. */
@Composable
private fun ResultsHeader(
    search: String,
    total: Int?,
    sellers: List<SellerHit>,
    onSeller: (String) -> Unit,
) {
    val locale = currentLocale()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (search.isNotBlank()) {
            Text(
                stringResource(R.string.feature_catalogue_results_for, search),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (total != null) {
            Text(
                pluralStringResource(R.plurals.feature_catalogue_count, total, formatCount(total, locale)),
                style = MaterialTheme.typography.bodySmall,
                color = BookStoreTheme.colors.textMuted,
            )
        }
        if (sellers.isNotEmpty()) SellerHits(sellers, onSeller)
    }
}

@Composable
private fun SellerHits(
    sellers: List<SellerHit>,
    onSeller: (String) -> Unit,
) {
    val locale = currentLocale()
    Text(
        stringResource(R.string.feature_catalogue_sellers),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(sellers, key = { it.username }) { seller ->
            OutlinedCard(onClick = { onSeller(seller.username) }) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SellerAvatar(seller.avatar, seller.username)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(seller.username, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            pluralStringResource(R.plurals.feature_catalogue_seller_books, seller.books, formatCount(seller.books, locale)),
                            style = MaterialTheme.typography.bodySmall,
                            color = BookStoreTheme.colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

/** The seller's photo, or their initial while there is none. */
@Composable
private fun SellerAvatar(
    url: String?,
    name: String,
) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        if (url !=
            null
        ) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
