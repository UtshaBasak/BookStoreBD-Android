package io.github.utshabasak.bookstorebd.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.sizedImageUrl
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A book's cover at the size it is drawn, from Cloudinary in that width, with
 * the shop's placeholder while it loads or when there is none. Decorative by
 * default: the title next to it already says what it is.
 */
@Composable
fun BookCover(
    url: String?,
    drawnWidth: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val widthPx = with(LocalDensity.current) { drawnWidth.toPx() }.roundToInt().coerceAtLeast(1)
    val placeholder = painterResource(R.drawable.core_ui_book_placeholder)
    AsyncImage(
        model = url?.let { sizedImageUrl(it, widthPx) },
        contentDescription = contentDescription,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .aspectRatio(COVER_RATIO)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** Covers are drawn 2:3, as books are. */
const val COVER_RATIO = 2f / 3f

/**
 * What a copy costs: the sale price, and when there is a discount the list
 * price struck through and the share off. Read aloud as one sentence.
 */
@Composable
fun PriceTag(
    book: Book,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val locale = currentLocale()
    val sale = formatTaka(book.salePrice, locale)
    val list = formatTaka(book.price, locale)
    val spoken =
        if (book.hasDeal) {
            stringResource(R.string.core_ui_price_with_discount, sale, list, formatCount(book.discountPercent, locale))
        } else {
            stringResource(R.string.core_ui_price, sale)
        }
    // On a narrow card the badge moves to the next line rather than breaking in two.
    FlowRow(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = sale,
            maxLines = 1,
            style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = BookStoreTheme.colors.price,
        )
        if (book.hasDeal) {
            Text(
                text = list,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = BookStoreTheme.colors.listPrice,
                textDecoration = TextDecoration.LineThrough,
            )
            DiscountBadge(book.discountPercent, locale)
        }
    }
}

@Composable
private fun DiscountBadge(
    percent: Int,
    locale: Locale,
) {
    Text(
        text = stringResource(R.string.core_ui_percent_off, formatCount(percent, locale)),
        maxLines = 1,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** "★ 4.5 (12)", or nothing when nobody has rated it yet. */
@Composable
fun RatingLine(
    average: Double,
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val locale = currentLocale()
    val shown = String.format(locale, "%.1f", average)
    val spoken = pluralStringResource(R.plurals.core_ui_rating, count, shown, formatCount(count, locale))
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = BookStoreTheme.colors.star, modifier = Modifier.size(16.dp))
        Text(shown, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("(${formatCount(count, locale)})", style = MaterialTheme.typography.labelSmall, color = BookStoreTheme.colors.textMuted)
    }
}

/** "Sold out" over a cover, or a small tag for a second-hand copy. */
@Composable
fun CoverBadge(
    text: String,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (emphasised) MaterialTheme.colorScheme.inverseSurface else BookStoreTheme.colors.brandFill)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (emphasised) MaterialTheme.colorScheme.inverseOnSurface else androidx.compose.ui.graphics.Color.White,
        )
    }
}
