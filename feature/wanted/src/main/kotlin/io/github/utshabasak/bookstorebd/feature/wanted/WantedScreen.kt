package io.github.utshabasak.bookstorebd.feature.wanted

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.WantedDraft
import io.github.utshabasak.bookstorebd.core.model.WantedItem
import io.github.utshabasak.bookstorebd.core.model.WantedSort
import io.github.utshabasak.bookstorebd.core.model.WantedStatus
import io.github.utshabasak.bookstorebd.core.ui.BookRowSkeleton
import io.github.utshabasak.bookstorebd.core.ui.EmptyState
import io.github.utshabasak.bookstorebd.core.ui.ErrorState
import io.github.utshabasak.bookstorebd.core.ui.SkeletonScreen
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatDate
import io.github.utshabasak.bookstorebd.core.ui.readableWidth

data class WantedActions(
    val onBack: () -> Unit,
    val onSearch: (String) -> Unit,
    val onStatus: (WantedStatus) -> Unit,
    val onSort: (WantedSort) -> Unit,
    val onMineOnly: (Boolean) -> Unit,
    val onToggle: (WantedItem) -> Unit,
    val onListIt: (WantedItem) -> Unit = {},
    val onOpenBook: (String) -> Unit,
    val onOpenForm: () -> Unit,
    val onCloseForm: () -> Unit,
    val onEditForm: ((WantedDraft) -> WantedDraft) -> Unit,
    val onAsk: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WantedScreen(
    state: WantedUiState,
    items: LazyPagingItems<WantedItem>,
    actions: WantedActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_wanted_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_wanted_back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onOpenForm,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.feature_wanted_ask)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = if (state.query.status == WantedStatus.Found) 1 else 0) {
                Tab(selected = state.query.status == WantedStatus.Open, onClick = { actions.onStatus(WantedStatus.Open) }, text = {
                    Text(stringResource(R.string.feature_wanted_open))
                })
                Tab(selected = state.query.status == WantedStatus.Found, onClick = { actions.onStatus(WantedStatus.Found) }, text = {
                    Text(stringResource(R.string.feature_wanted_found))
                })
            }
            OutlinedTextField(
                value = state.searchText,
                onValueChange = actions.onSearch,
                placeholder = { Text(stringResource(R.string.feature_wanted_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = state.query.sort == WantedSort.Popular, onClick = {
                        actions.onSort(WantedSort.Popular)
                    }, label = {
                        Text(stringResource(R.string.feature_wanted_most_wanted))
                    })
                }
                item {
                    FilterChip(selected = state.query.sort == WantedSort.Newest, onClick = { actions.onSort(WantedSort.Newest) }, label = {
                        Text(stringResource(R.string.feature_wanted_newest))
                    })
                }
                item {
                    FilterChip(selected = state.query.mineOnly, onClick = { actions.onMineOnly(!state.query.mineOnly) }, label = {
                        Text(stringResource(R.string.feature_wanted_mine))
                    })
                }
            }
            val refresh = items.loadState.refresh
            PullToRefreshBox(
                isRefreshing = refresh is LoadState.Loading && items.itemCount > 0,
                onRefresh = { items.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    refresh is LoadState.Loading && items.itemCount == 0 -> {
                        SkeletonScreen { Column { repeat(PLACEHOLDERS) { BookRowSkeleton() } } }
                    }

                    refresh is LoadState.Error && items.itemCount == 0 -> {
                        ErrorState(refresh.error.appError(), onRetry = items::retry)
                    }

                    refresh is LoadState.NotLoading && items.itemCount == 0 -> {
                        EmptyState(
                            icon = Icons.AutoMirrored.Outlined.ManageSearch,
                            title = stringResource(R.string.feature_wanted_empty),
                            body = stringResource(R.string.feature_wanted_empty_body),
                        )
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = FAB_SPACE),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize().readableWidth(),
                        ) {
                            items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                                items[index]?.let { WantedCard(it, state.mine[it.id], actions) }
                            }
                            if (items.loadState.append is LoadState.Loading) {
                                item {
                                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    state.form?.let { form -> AskSheet(form, state, actions) }
}

@Composable
private fun WantedCard(
    item: WantedItem,
    override: Boolean?,
    actions: WantedActions,
) {
    val locale = currentLocale()
    val wanted = override ?: item.wantedByMe
    val count =
        (
            item.count +
                if (override == null || override == item.wantedByMe) {
                    0
                } else if (override) {
                    1
                } else {
                    -1
                }
        ).coerceAtLeast(0)
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (item.author.isNotBlank()) {
                Text(
                    item.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BookStoreTheme.colors.textMuted,
                )
            }
            if (item.isbn.isNotBlank()) {
                Text(
                    stringResource(R.string.feature_wanted_isbn, item.isbn),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (item.details.isNotBlank()) Text(item.details, style = MaterialTheme.typography.bodyMedium)
            Text(
                pluralStringResource(R.plurals.feature_wanted_people, count, formatCount(count, locale)) + " · " +
                    formatDate(item.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = BookStoreTheme.colors.textMuted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.status == WantedStatus.Found && item.foundBookId != null) {
                    Button(onClick = { actions.onOpenBook(item.foundBookId!!) }, modifier = Modifier.heightIn(min = 40.dp)) {
                        Text(stringResource(R.string.feature_wanted_see_it))
                    }
                } else if (wanted) {
                    FilledTonalButton(onClick = { actions.onToggle(item) }, modifier = Modifier.heightIn(min = 40.dp)) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.feature_wanted_you_want), Modifier.padding(start = 6.dp))
                    }
                } else {
                    OutlinedButton(onClick = { actions.onToggle(item) }, modifier = Modifier.heightIn(min = 40.dp)) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.feature_wanted_want_too), Modifier.padding(start = 6.dp))
                    }
                }
                // As on the website: a seller with the book can list it straight away, filled in.
                if (item.status == WantedStatus.Open) {
                    TextButton(onClick = { actions.onListIt(item) }, modifier = Modifier.heightIn(min = 40.dp)) {
                        Text(stringResource(R.string.feature_wanted_list_it))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AskSheet(
    form: WantedDraft,
    state: WantedUiState,
    actions: WantedActions,
) {
    ModalBottomSheet(onDismissRequest = actions.onCloseForm, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).imePadding().navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.feature_wanted_ask_title),
                style = MaterialTheme.typography.titleLarge,
                modifier =
                    Modifier.semantics {
                        heading()
                    },
            )
            Text(
                stringResource(R.string.feature_wanted_ask_body),
                style = MaterialTheme.typography.bodyMedium,
                color = BookStoreTheme.colors.textMuted,
            )
            OutlinedTextField(
                value = form.title,
                onValueChange = { v -> actions.onEditForm { it.copy(title = v.take(TITLE_LIMIT)) } },
                label = { Text(stringResource(R.string.feature_wanted_book_title)) },
                isError = state.titleProblem,
                supportingText = if (state.titleProblem) ({ Text(stringResource(R.string.feature_wanted_title_problem)) }) else null,
                singleLine = true,
                keyboardOptions = NAME_KEYBOARD,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.author,
                onValueChange = { v -> actions.onEditForm { it.copy(author = v.take(AUTHOR_LIMIT)) } },
                label = { Text(stringResource(R.string.feature_wanted_author)) },
                singleLine = true,
                keyboardOptions = NAME_KEYBOARD,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.isbn,
                onValueChange = { v ->
                    actions.onEditForm { it.copy(isbn = v.filter { c -> c.isDigit() || c in "Xx- " }.take(ISBN_LIMIT)) }
                },
                label = { Text(stringResource(R.string.feature_wanted_isbn_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.details,
                onValueChange = { v -> actions.onEditForm { it.copy(details = v.take(DETAILS_LIMIT)) } },
                label = { Text(stringResource(R.string.feature_wanted_details)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = actions.onAsk, enabled = !state.asking, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                if (state.asking) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.feature_wanted_ask_send))
                }
            }
        }
    }
}

private const val PLACEHOLDERS = 5
private val FAB_SPACE = 96.dp
private const val TITLE_LIMIT = 200
private const val AUTHOR_LIMIT = 120
private const val ISBN_LIMIT = 20
private const val DETAILS_LIMIT = 300

/** Book titles and author names: capitalised words, and no autocorrect turning names into other words. */
private val NAME_KEYBOARD =
    KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false, imeAction = ImeAction.Next)
