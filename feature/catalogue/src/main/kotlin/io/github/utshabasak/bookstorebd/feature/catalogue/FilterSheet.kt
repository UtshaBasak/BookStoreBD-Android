package io.github.utshabasak.bookstorebd.feature.catalogue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.CatalogueQuery
import io.github.utshabasak.bookstorebd.core.model.CategoryGroup
import io.github.utshabasak.bookstorebd.core.ui.BOOK_CONDITIONS
import io.github.utshabasak.bookstorebd.core.ui.conditionLabel

/**
 * The catalogue's filters, edited as a draft and applied together: type,
 * condition, categories, price, rating, stock and deals.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FilterSheet(
    current: CatalogueQuery,
    groups: List<CategoryGroup>,
    onApply: (CatalogueQuery) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(current.type) }
    var condition by rememberSaveable { mutableStateOf(current.condition) }
    var categories by rememberSaveable { mutableStateOf(current.categories.toList()) }
    var minPrice by rememberSaveable { mutableStateOf(current.minPrice?.toString().orEmpty()) }
    var maxPrice by rememberSaveable { mutableStateOf(current.maxPrice?.toString().orEmpty()) }
    var rating by rememberSaveable { mutableStateOf(current.minRating) }
    var inStock by rememberSaveable { mutableStateOf(current.inStockOnly) }
    var deals by rememberSaveable { mutableStateOf(current.dealsOnly) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(R.string.feature_catalogue_filters),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )

            Section(R.string.feature_catalogue_filter_type)
            val typeOptions =
                listOf(
                    null to R.string.feature_catalogue_any,
                    BookType.New to R.string.feature_catalogue_new,
                    BookType.Old to R.string.feature_catalogue_old,
                )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                typeOptions.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = type == value,
                        onClick = {
                            type = value
                            if (value != BookType.Old) condition = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, typeOptions.size),
                    ) { Text(stringResource(label)) }
                }
            }

            if (type == BookType.Old) {
                Section(R.string.feature_catalogue_filter_condition)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BOOK_CONDITIONS.forEach { grade ->
                        FilterChip(selected = condition == grade, onClick = {
                            condition =
                                if (condition ==
                                    grade
                                ) {
                                    null
                                } else {
                                    grade
                                }
                        }, label = {
                            Text(conditionLabel(grade))
                        })
                    }
                }
            }

            Section(R.string.feature_catalogue_filter_price)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PriceField(minPrice, { minPrice = it }, R.string.feature_catalogue_min_price, Modifier.weight(1f))
                PriceField(maxPrice, { maxPrice = it }, R.string.feature_catalogue_max_price, Modifier.weight(1f))
            }

            Section(R.string.feature_catalogue_filter_rating)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf<Int?>(null, 5, 4, 3, 2, 1).forEach { floor ->
                    FilterChip(selected = rating == floor, onClick = { rating = floor }, label = {
                        Text(
                            when (floor) {
                                null -> stringResource(R.string.feature_catalogue_any)
                                MAX_STARS -> stringResource(R.string.feature_catalogue_stars, floor)
                                else -> stringResource(R.string.feature_catalogue_stars_up, floor)
                            },
                        )
                    })
                }
            }

            SwitchRow(R.string.feature_catalogue_in_stock_only, inStock) { inStock = it }
            SwitchRow(R.string.feature_catalogue_deals_only, deals) { deals = it }

            groups.forEach { group ->
                Text(
                    "${group.emoji} ${group.name}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.items.forEach { category ->
                        // A website link names its category in lower case.
                        val selected = categories.any { it.equals(category, ignoreCase = true) }
                        FilterChip(
                            selected = selected,
                            onClick = {
                                categories =
                                    if (selected) categories.filterNot { it.equals(category, ignoreCase = true) } else categories + category
                            },
                            label = { Text(category) },
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = { onApply(CatalogueQuery()) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.feature_catalogue_clear_filters))
                }
                Button(
                    onClick = {
                        onApply(
                            CatalogueQuery(
                                type = type,
                                condition = condition,
                                categories = categories.toSet(),
                                minPrice = minPrice.toIntOrNull(),
                                maxPrice = maxPrice.toIntOrNull(),
                                minRating = rating,
                                inStockOnly = inStock,
                                dealsOnly = deals,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.feature_catalogue_show_results)) }
            }
        }
    }
}

@Composable
private fun Section(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        modifier =
            Modifier
                .padding(top = 20.dp, bottom = 8.dp)
                .semantics { heading() },
    )
}

@Composable
private fun PriceField(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(MAX_PRICE_DIGITS)) },
        label = { Text(stringResource(label)) },
        prefix = { Text("৳") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun SwitchRow(
    label: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private const val MAX_PRICE_DIGITS = 6

/** The top of the rating scale: "5★" alone, with no "and up". */
private const val MAX_STARS = 5
