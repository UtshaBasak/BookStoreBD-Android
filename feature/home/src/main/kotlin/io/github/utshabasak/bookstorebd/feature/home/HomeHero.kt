package io.github.utshabasak.bookstorebd.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Recycling
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppConfig
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.CatalogueSort
import io.github.utshabasak.bookstorebd.core.model.CategoryCount
import io.github.utshabasak.bookstorebd.core.model.CategoryGroup
import io.github.utshabasak.bookstorebd.core.ui.ShopPerks
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.navigation.CatalogueRoute

/** The website's quick links under the header: every category, new, second-hand, deals, in stock, and the Wanted board. */
@Composable
internal fun QuickChips(
    onCategories: () -> Unit,
    onOpenCatalogue: (CatalogueRoute) -> Unit,
    onWanted: () -> Unit,
) {
    val chips =
        listOf(
            Triple(Icons.Outlined.Category, R.string.feature_home_chip_categories, onCategories),
            Triple(Icons.Outlined.AutoAwesome, R.string.feature_home_chip_new) { onOpenCatalogue(CatalogueRoute(type = BookType.New)) },
            Triple(Icons.Outlined.Recycling, R.string.feature_home_chip_old) { onOpenCatalogue(CatalogueRoute(type = BookType.Old)) },
            Triple(Icons.Outlined.Bolt, R.string.feature_home_deals) {
                onOpenCatalogue(CatalogueRoute(dealsOnly = true, sort = CatalogueSort.DealPercent))
            },
            Triple(Icons.Outlined.Inventory2, R.string.feature_home_chip_in_stock) { onOpenCatalogue(CatalogueRoute(inStockOnly = true)) },
            Triple(Icons.Outlined.TrackChanges, R.string.feature_home_chip_wanted, onWanted),
        )
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips, key = { it.second }) { (icon, label, onClick) ->
            AssistChip(
                onClick = onClick,
                label = { Text(stringResource(label)) },
                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
            )
        }
    }
}

/** The first thing on the website's home page: what the shop is, the two ways in, and its promises. */
@Composable
internal fun Hero(
    config: AppConfig?,
    onBrowse: () -> Unit,
    onSell: () -> Unit,
) {
    val highlight = stringResource(R.string.feature_home_hero_highlight)
    val title = stringResource(R.string.feature_home_hero_title, highlight)
    val start = title.indexOf(highlight)
    val primary = MaterialTheme.colorScheme.primary
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.feature_home_kicker), style = MaterialTheme.typography.labelLarge, color = primary)
            Text(
                buildAnnotatedString {
                    if (start < 0) {
                        append(title)
                    } else {
                        append(title.substring(0, start))
                        withStyle(SpanStyle(color = primary)) { append(highlight) }
                        append(title.substring(start + highlight.length))
                    }
                },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.feature_home_hero_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = BookStoreTheme.colors.textMuted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBrowse) { Text(stringResource(R.string.feature_home_browse_all)) }
                OutlinedButton(onClick = onSell) { Text(stringResource(R.string.feature_home_sell)) }
            }
            config?.let { ShopPerks(returnDays = it.returnWindowDays, delivery = it.delivery) }
        }
    }
}

/**
 * The categories with listings, busiest first, then the website's own starter
 * picks, up to [MAX_TILES], as the website's "Shop by category" row has them.
 */
internal fun categoryTiles(counted: List<CategoryCount>): List<CategoryCount> {
    val named = counted.map { it.name.lowercase() }.toSet()
    val extra = STARTER_CATEGORIES.filter { it.lowercase() !in named }.map { CategoryCount(it, 0) }
    return (counted + extra).take(MAX_TILES)
}

/** One tile per category: its group's emoji, its name, and how many books it has. */
@Composable
internal fun CategoryTiles(
    tiles: List<CategoryCount>,
    groups: List<CategoryGroup>,
    onPick: (String) -> Unit,
) {
    val locale = currentLocale()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(tiles, key = { it.name }) { tile ->
            val emoji = groups.firstOrNull { group -> group.items.any { it.equals(tile.name, ignoreCase = true) } }?.emoji ?: "📚"
            Card(
                onClick = { onPick(tile.name) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.width(TILE_WIDTH).heightIn(min = TILE_HEIGHT),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(emoji, style = MaterialTheme.typography.headlineSmall)
                    Text(tile.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    if (tile.books > 0) {
                        Text(
                            pluralStringResource(R.plurals.feature_home_books, tile.books, formatCount(tile.books, locale)),
                            style = MaterialTheme.typography.bodySmall,
                            color = BookStoreTheme.colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

/** Every category, grouped as on the website's Categories menu. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CategoriesSheet(
    groups: List<CategoryGroup>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.feature_home_chip_categories),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(groups, key = { it.name }) { group ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${group.emoji} ${group.name}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.items.forEach { category -> SuggestionChip(onClick = { onPick(category) }, label = { Text(category) }) }
                    }
                }
            }
        }
    }
}

/** Latest books while the shop has none: an invitation to be the first seller, as on the website. */
@Composable
internal fun EmptyShelf(onSell: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.feature_home_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.feature_home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = BookStoreTheme.colors.textMuted,
        )
        Button(onClick = onSell) { Text(stringResource(R.string.feature_home_list_book)) }
    }
}

private val STARTER_CATEGORIES =
    listOf(
        "Novels",
        "Textbooks",
        "Admission Test",
        "Programming",
        "Self-Help",
        "Mystery",
        "History",
        "Children's",
        "Islamic",
        "Poetry",
        "Comics",
        "Cooking",
    )
private const val MAX_TILES = 24
private val TILE_WIDTH = 132.dp
private val TILE_HEIGHT = 112.dp
