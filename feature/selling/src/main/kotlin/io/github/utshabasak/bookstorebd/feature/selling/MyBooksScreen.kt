package io.github.utshabasak.bookstorebd.feature.selling

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.Listing
import io.github.utshabasak.bookstorebd.core.model.Pricing
import io.github.utshabasak.bookstorebd.core.ui.BookCover
import io.github.utshabasak.bookstorebd.core.ui.BookRowSkeleton
import io.github.utshabasak.bookstorebd.core.ui.EmptyState
import io.github.utshabasak.bookstorebd.core.ui.ErrorState
import io.github.utshabasak.bookstorebd.core.ui.SkeletonScreen
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatTaka

data class MyBooksActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onSearch: (String) -> Unit,
    val onListBook: () -> Unit,
    val onOpenBook: (String) -> Unit,
    val onSave: (Listing, ListingChange) -> Unit,
    val onDelete: (Listing) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBooksScreen(
    state: MyBooksUiState,
    actions: MyBooksActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_selling_my_books)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_selling_back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onListBook,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.feature_selling_list_title)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val inner = Modifier.fillMaxSize().padding(padding)
        val listings = state.listings
        when {
            listings == null && state.error != null -> {
                ErrorState(state.error, onRetry = actions.onRefresh, modifier = inner)
            }

            listings == null -> {
                SkeletonScreen(inner) { Column { repeat(PLACEHOLDERS) { BookRowSkeleton() } } }
            }

            listings.isEmpty() -> {
                EmptyState(
                    icon = Icons.Outlined.Inventory2,
                    title = stringResource(R.string.feature_selling_no_books),
                    body = stringResource(R.string.feature_selling_no_books_body),
                    modifier = inner,
                )
            }

            else -> {
                PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = actions.onRefresh, modifier = inner) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = FAB_SPACE)) {
                        item { Summary(listings, state.search, actions.onSearch) }
                        items(state.shown, key = { it.book.id }) { listing ->
                            ListingRow(listing) { editing = listing.book.id }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
    listings(state).firstOrNull { it.book.id == editing }?.let { listing ->
        EditListingSheet(
            listing = listing,
            saving = state.saving == listing.book.id,
            onDismiss = { editing = null },
            onOpen = {
                editing = null
                actions.onOpenBook(listing.book.id)
            },
            onSave = { change ->
                actions.onSave(listing, change)
                editing = null
            },
            onDelete = {
                actions.onDelete(listing)
                editing = null
            },
        )
    }
}

private fun listings(state: MyBooksUiState): List<Listing> = state.listings.orEmpty()

@Composable
private fun Summary(
    listings: List<Listing>,
    search: String,
    onSearch: (String) -> Unit,
) {
    val locale = currentLocale()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(
                R.string.feature_selling_summary,
                formatCount(listings.size, locale),
                formatCount(listings.count { it.book.inStock }, locale),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = BookStoreTheme.colors.textMuted,
        )
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            placeholder = { Text(stringResource(R.string.feature_selling_search_books)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ListingRow(
    listing: Listing,
    onClick: () -> Unit,
) {
    val locale = currentLocale()
    val book = listing.book
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BookCover(url = book.cover, drawnWidth = COVER_DP.dp, modifier = Modifier.width(COVER_DP.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(book.author, style = MaterialTheme.typography.bodySmall, color = BookStoreTheme.colors.textMuted, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatTaka(book.salePrice, locale), style = MaterialTheme.typography.titleSmall, color = BookStoreTheme.colors.price)
                if (book.hasDeal) {
                    Text(
                        formatTaka(book.price, locale),
                        style = MaterialTheme.typography.bodySmall,
                        color = BookStoreTheme.colors.textMuted,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (book.inStock) {
                        pluralStringResource(R.plurals.feature_selling_in_stock, book.stock, formatCount(book.stock, locale))
                    } else {
                        stringResource(R.string.feature_selling_sold_out)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (book.inStock) BookStoreTheme.colors.success else MaterialTheme.colorScheme.error,
                )
                if (listing.waiting > 0) {
                    Text(
                        pluralStringResource(R.plurals.feature_selling_waiting, listing.waiting, formatCount(listing.waiting, locale)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    stringResource(
                        R.string.feature_selling_demand,
                        formatCount(book.viewCount, locale),
                        formatCount(book.wishlistCount, locale),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = BookStoreTheme.colors.textMuted,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditListingSheet(
    listing: Listing,
    saving: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onSave: (ListingChange) -> Unit,
    onDelete: () -> Unit,
) {
    val book = listing.book
    var price by rememberSaveable(book.id) { mutableStateOf(book.price.toInt().toString()) }
    var stock by rememberSaveable(book.id) { mutableStateOf(book.stock.toString()) }
    var discount by remember(book.id) { mutableStateOf(listing.discount) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val change =
        ListingChange(
            price = price.toIntOrNull()?.takeIf { it != book.price.toInt() },
            stock = stock.toIntOrNull()?.takeIf { it != book.stock },
            discount = discount.takeIf { it != listing.discount },
        )
    val problem = price.toIntOrNull()?.let { Pricing.problem(it, discount) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(book.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(
                onClick = onOpen,
                contentPadding = PaddingValues(0.dp),
            ) { Text(stringResource(R.string.feature_selling_view_listing)) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(R.string.feature_selling_field_price, price, Modifier.weight(1f), prefix = "৳") { price = it }
                NumberField(R.string.feature_selling_field_stock, stock, Modifier.weight(1f)) { stock = it }
            }
            DiscountEditor(price = price.toIntOrNull(), discount = discount, onChange = { discount = it })
            Button(
                onClick = { onSave(change) },
                enabled = !saving && problem == null && price.isNotEmpty() && stock.isNotEmpty() && change != ListingChange(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        Modifier.padding(2.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.feature_selling_save))
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.feature_selling_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.feature_selling_delete_title)) },
            text = { Text(stringResource(R.string.feature_selling_delete_body, book.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.feature_selling_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.feature_selling_keep)) } },
        )
    }
}

@Composable
private fun NumberField(
    label: Int,
    value: String,
    modifier: Modifier,
    prefix: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(MAX_DIGITS)) },
        label = { Text(stringResource(label)) },
        prefix = prefix?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

private const val PLACEHOLDERS = 6
private const val COVER_DP = 56
private const val MAX_DIGITS = 6
private val FAB_SPACE = 96.dp
