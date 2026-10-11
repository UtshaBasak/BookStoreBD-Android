package io.github.utshabasak.bookstorebd.feature.selling

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.ListingDraft
import io.github.utshabasak.bookstorebd.core.model.Pricing
import io.github.utshabasak.bookstorebd.core.ui.BOOK_CONDITIONS
import io.github.utshabasak.bookstorebd.core.ui.conditionLabel
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatTaka

data class ListBookActions(
    val onBack: () -> Unit,
    val onEdit: ((ListingDraft) -> ListingDraft) -> Unit,
    val onToggleCategory: (String) -> Unit,
    val onAddPhotos: () -> Unit,
    val onRemovePhoto: (Uri) -> Unit,
    val onMakeCover: (Uri) -> Unit,
    val onAddBkash: () -> Unit,
    val onPublish: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListBookScreen(
    state: ListBookUiState,
    actions: ListBookActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_selling_list_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_selling_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Button(
                    onClick = actions.onPublish,
                    enabled = state.canSell == true && !state.publishing,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).heightIn(min = 52.dp),
                ) {
                    if (state.publishing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.feature_selling_publish))
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.canSell == false) item { BkashNeeded(actions.onAddBkash) }
            item { Photos(state.photos, ListingProblem.Photos in state.problems, actions) }
            item { Details(state, actions.onEdit) }
            item { Condition(state.draft, actions.onEdit) }
            item { Categories(state, actions.onToggleCategory) }
            item { PriceSection(state, actions.onEdit) }
        }
    }
}

@Composable
private fun BkashNeeded(onAdd: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.feature_selling_bkash_needed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                TextButton(
                    onClick = onAdd,
                    contentPadding = PaddingValues(0.dp),
                ) { Text(stringResource(R.string.feature_selling_bkash_add)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Photos(
    photos: List<Uri>,
    missing: Boolean,
    actions: ListBookActions,
) {
    val locale = currentLocale()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.feature_selling_photos))
        Text(
            stringResource(
                R.string.feature_selling_photos_hint,
                formatCount(photos.size, locale),
                formatCount(ListBookViewModel.MAX_PHOTOS, locale),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (missing) MaterialTheme.colorScheme.error else BookStoreTheme.colors.textMuted,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(photos) { index, uri -> PhotoTile(uri, cover = index == 0, actions) }
            if (photos.size < ListBookViewModel.MAX_PHOTOS) {
                item {
                    Box(
                        Modifier
                            .size(PHOTO_DP.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                BorderStroke(1.dp, if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
                                RoundedCornerShape(12.dp),
                            )
                            .clickable(onClick = actions.onAddPhotos),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = stringResource(R.string.feature_selling_add_photos))
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoTile(
    uri: Uri,
    cover: Boolean,
    actions: ListBookActions,
) {
    val makeCover = stringResource(R.string.feature_selling_make_cover)
    Box(Modifier.size(PHOTO_DP.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = makeCover) { actions.onMakeCover(uri) }) {
        AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (cover) {
            Text(
                stringResource(R.string.feature_selling_cover),
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.ui.graphics.Color.White,
                modifier =
                    Modifier.align(Alignment.BottomStart).background(BookStoreTheme.colors.brandFill).padding(
                        horizontal = 6.dp,
                        vertical = 2.dp,
                    ),
            )
        }
        // A full-size touch target, with only a small dot drawn behind the cross.
        IconButton(onClick = { actions.onRemovePhoto(uri) }, modifier = Modifier.align(Alignment.TopEnd)) {
            Box(Modifier.size(24.dp).background(MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.feature_selling_remove_photo),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun Details(
    state: ListBookUiState,
    onEdit: ((ListingDraft) -> ListingDraft) -> Unit,
) {
    val draft = state.draft
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.feature_selling_details))
        Field(R.string.feature_selling_field_title, draft.title, ListingProblem.Title in state.problems, words = true) { v ->
            onEdit { it.copy(title = v.take(SHORT)) }
        }
        Field(R.string.feature_selling_field_author, draft.author, ListingProblem.Author in state.problems, words = true) { v ->
            onEdit { it.copy(author = v.take(SHORT)) }
        }
        Field(
            R.string.feature_selling_field_publisher,
            draft.publisher,
            words = true,
        ) { v -> onEdit { it.copy(publisher = v.take(SHORT)) } }
        Field(R.string.feature_selling_field_language, draft.language, words = true) { v -> onEdit { it.copy(language = v.take(SHORT)) } }
        Field(R.string.feature_selling_field_country, draft.country, words = true) { v -> onEdit { it.copy(country = v.take(SHORT)) } }
        Field(
            R.string.feature_selling_field_isbn,
            draft.isbn,
            number = true,
        ) { v -> onEdit { it.copy(isbn = v.filter { c -> c.isDigit() || c in "Xx- " }.take(SHORT)) } }
        Field(
            R.string.feature_selling_field_pages,
            draft.pages,
            number = true,
        ) { v -> onEdit { it.copy(pages = v.filter(Char::isDigit).take(PAGES_DIGITS)) } }
        OutlinedTextField(
            value = draft.description,
            onValueChange = { v -> onEdit { it.copy(description = v.take(LONG)) } },
            label = { Text(stringResource(R.string.feature_selling_field_description)) },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Field(
    label: Int,
    value: String,
    missing: Boolean = false,
    words: Boolean = false,
    number: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = missing,
        supportingText = if (missing) ({ Text(stringResource(R.string.feature_selling_required)) }) else null,
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                capitalization = if (words) KeyboardCapitalization.Words else KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
            ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Condition(
    draft: ListingDraft,
    onEdit: ((ListingDraft) -> ListingDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.feature_selling_type))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(BookType.New, BookType.Old).forEachIndexed { index, type ->
                SegmentedButton(
                    selected = draft.type == type,
                    onClick = { onEdit { it.copy(type = type) } },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) {
                    Text(
                        stringResource(if (type == BookType.New) R.string.feature_selling_type_new else R.string.feature_selling_type_old),
                    )
                }
            }
        }
        if (draft.type == BookType.Old) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BOOK_CONDITIONS.forEach { grade ->
                    FilterChip(
                        selected = draft.condition == grade,
                        onClick = { onEdit { it.copy(condition = grade) } },
                        label = { Text(conditionLabel(grade)) },
                    )
                }
            }
            OutlinedTextField(
                value = draft.conditionDetails,
                onValueChange = { v -> onEdit { it.copy(conditionDetails = v.take(LONG)) } },
                label = { Text(stringResource(R.string.feature_selling_condition_details)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Categories(
    state: ListBookUiState,
    onToggle: (String) -> Unit,
) {
    val missing = ListingProblem.Categories in state.problems
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.feature_selling_categories))
        Text(
            stringResource(R.string.feature_selling_categories_hint),
            style = MaterialTheme.typography.bodySmall,
            color = if (missing) MaterialTheme.colorScheme.error else BookStoreTheme.colors.textMuted,
        )
        state.groups.forEach { group ->
            Text("${group.emoji} ${group.name}", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.items.forEach { name ->
                    FilterChip(selected = name in state.draft.categories, onClick = { onToggle(name) }, label = { Text(name) })
                }
            }
        }
    }
}

@Composable
private fun PriceSection(
    state: ListBookUiState,
    onEdit: ((ListingDraft) -> ListingDraft) -> Unit,
) {
    val locale = currentLocale()
    val price = state.price
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.feature_selling_price))
        OutlinedTextField(
            value = state.draft.price,
            onValueChange = { v -> onEdit { it.copy(price = v.filter(Char::isDigit).take(PRICE_DIGITS)) } },
            label = { Text(stringResource(R.string.feature_selling_field_price)) },
            prefix = { Text("৳") },
            isError = ListingProblem.Price in state.problems,
            supportingText = {
                val sale = state.salePrice
                if (sale != null) {
                    Text(
                        stringResource(
                            R.string.feature_selling_you_receive,
                            formatTaka(Pricing.sellerReceives(sale.toDouble()), locale),
                            Pricing.SELLER_FEE_PERCENT,
                        ),
                    )
                } else if (ListingProblem.Price in state.problems) {
                    Text(stringResource(R.string.feature_selling_required))
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        DiscountEditor(price = price, discount = state.draft.discount, onChange = { d -> onEdit { it.copy(discount = d) } })
    }
}

private const val PHOTO_DP = 96
private const val SHORT = 200
private const val LONG = 2000
private const val PAGES_DIGITS = 5
private const val PRICE_DIGITS = 6
