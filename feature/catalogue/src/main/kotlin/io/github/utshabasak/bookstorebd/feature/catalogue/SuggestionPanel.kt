package io.github.utshabasak.bookstorebd.feature.catalogue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.model.Suggestions
import io.github.utshabasak.bookstorebd.core.ui.BookCover
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatTaka
import io.github.utshabasak.bookstorebd.core.ui.readableWidth

/**
 * What the search box offers: with nothing typed, recent and popular searches;
 * while typing, matching books, writers, categories and sellers. A Bangla word
 * finds English titles and the other way round, on the server.
 */
@Composable
internal fun SuggestionPanel(
    text: String,
    suggestions: Suggestions?,
    recent: List<String>,
    popular: List<String>,
    onSearch: (String) -> Unit,
    onForget: (String) -> Unit,
    onBook: (String) -> Unit,
    onAuthor: (String) -> Unit,
    onCategory: (String) -> Unit,
    onSeller: (String) -> Unit,
    onClearRecent: () -> Unit = {},
    voice: VoiceChoice? = null,
) {
    val locale = currentLocale()
    LazyColumn(Modifier.fillMaxSize().readableWidth()) {
        if (voice != null) item(key = "voice") { VoiceLanguageRow(voice) }
        if (text.isBlank()) {
            if (recent.isNotEmpty()) item { Heading(stringResource(R.string.feature_catalogue_recent)) }
            items(recent, key = { "recent:$it" }) { term ->
                ListItem(
                    headlineContent = { Text(term) },
                    leadingContent = { Icon(Icons.Outlined.History, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { onForget(term) }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.feature_catalogue_forget, term))
                        }
                    },
                    modifier = Modifier.clickableRow { onSearch(term) },
                )
            }
            if (recent.isNotEmpty()) {
                item(key = "clear-recent") {
                    TextButton(onClick = onClearRecent, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(stringResource(R.string.feature_catalogue_clear_recent))
                    }
                }
            }
            if (popular.isNotEmpty()) item { Heading(stringResource(R.string.feature_catalogue_popular)) }
            items(popular, key = { "popular:$it" }) { term ->
                SuggestionRow(Icons.AutoMirrored.Outlined.TrendingUp, term) { onSearch(term) }
            }
        } else {
            item {
                SuggestionRow(Icons.AutoMirrored.Outlined.TrendingUp, stringResource(R.string.feature_catalogue_search_for, text.trim())) {
                    onSearch(text)
                }
            }
            val found = suggestions ?: return@LazyColumn
            if (found.books.isNotEmpty()) item { Heading(stringResource(R.string.feature_catalogue_books)) }
            items(found.books, key = { "book:${it.id}" }) { book ->
                ListItem(
                    headlineContent = { Text(book.title, maxLines = 2) },
                    supportingContent = { Text("${book.author} · ${formatTaka(book.salePrice, locale)}") },
                    leadingContent = { BookCover(book.cover, drawnWidth = 40.dp, modifier = Modifier.width(40.dp)) },
                    modifier = Modifier.clickableRow { onBook(book.id) },
                )
            }
            if (found.authors.isNotEmpty()) item { Heading(stringResource(R.string.feature_catalogue_writers)) }
            items(found.authors, key = { "author:${it.name}" }) { author ->
                SuggestionRow(Icons.Outlined.Person, author.name, formatCount(author.books, locale)) { onAuthor(author.name) }
            }
            if (found.categories.isNotEmpty()) item { Heading(stringResource(R.string.feature_catalogue_categories)) }
            items(found.categories, key = { "category:${it.name}" }) { category ->
                SuggestionRow(Icons.Outlined.Category, category.name, formatCount(category.books, locale)) { onCategory(category.name) }
            }
            if (found.sellers.isNotEmpty()) item { Heading(stringResource(R.string.feature_catalogue_sellers)) }
            items(found.sellers, key = { "seller:${it.username}" }) { seller ->
                SuggestionRow(Icons.Outlined.Storefront, seller.username, formatCount(seller.books, locale)) { onSeller(seller.username) }
            }
        }
    }
}

/** The language voice search listens in, and how to change it. */
internal class VoiceChoice(
    val language: String,
    val onChange: (String) -> Unit,
)

/** English or Bangla for voice search, as the website offers beside its microphone; the choice is remembered. */
@Composable
private fun VoiceLanguageRow(voice: VoiceChoice) {
    val label = stringResource(R.string.feature_catalogue_voice_language)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).semantics(mergeDescendants = false) {
            contentDescription =
                label
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        listOf(
            VOICE_ENGLISH to R.string.feature_catalogue_lang_en,
            VOICE_BANGLA to R.string.feature_catalogue_lang_bn,
        ).forEach { (tag, name) ->
            FilterChip(selected = voice.language == tag, onClick = { voice.onChange(tag) }, label = { Text(stringResource(name)) })
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                .semantics { heading() },
    )
}

@Composable
private fun SuggestionRow(
    icon: ImageVector,
    text: String,
    count: String? = null,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = count?.let { { Text(it, style = MaterialTheme.typography.labelMedium) } },
        modifier = Modifier.clickableRow(onClick),
    )
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = clickable(role = Role.Button, onClick = onClick)
