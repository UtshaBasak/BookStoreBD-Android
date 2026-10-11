package io.github.utshabasak.bookstorebd.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.BookType

/**
 * A book on a shelf or in a grid: cover, title, author, price and rating, and
 * the heart for the wishlist. The whole card opens the book; the heart is its
 * own 48dp target.
 */
@Composable
fun BookCard(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 156.dp,
    inWishlist: Boolean = false,
    onToggleWishlist: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .width(width)
                .clickable(onClickLabel = stringResource(R.string.core_ui_open_book), role = Role.Button, onClick = onClick)
                .padding(bottom = 8.dp),
    ) {
        Box {
            BookCover(url = book.cover, drawnWidth = width, modifier = Modifier.fillMaxWidth())
            Column(Modifier.align(Alignment.TopStart).padding(6.dp)) {
                if (!book.inStock) {
                    CoverBadge(stringResource(R.string.core_ui_sold_out), emphasised = true)
                } else if (book.type == BookType.Old) {
                    CoverBadge(stringResource(R.string.core_ui_second_hand))
                }
            }
            if (onToggleWishlist != null) {
                WishlistButton(
                    inWishlist = inWishlist,
                    title = book.title,
                    onToggle = onToggleWishlist,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = book.author,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = BookStoreTheme.colors.textMuted,
        )
        Spacer(Modifier.height(4.dp))
        PriceTag(book)
        RatingLine(book.ratingAverage, book.ratingCount, Modifier.padding(top = 2.dp))
    }
}

/** The heart: on or off, announced as a toggle with the book's title. */
@Composable
fun WishlistButton(
    inWishlist: Boolean,
    title: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label =
        if (inWishlist) {
            stringResource(R.string.core_ui_remove_from_wishlist, title)
        } else {
            stringResource(R.string.core_ui_add_to_wishlist, title)
        }
    val state = stringResource(if (inWishlist) R.string.core_ui_in_wishlist else R.string.core_ui_not_in_wishlist)
    IconButton(
        onClick = onToggle,
        modifier = modifier.semantics { stateDescription = state },
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                contentColor = if (inWishlist) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    ) {
        Icon(
            imageVector = if (inWishlist) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** A book as a row: cart, wishlist and order summaries. */
@Composable
fun BookRow(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    below: @Composable () -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.core_ui_open_book), role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BookCover(url = book.cover, drawnWidth = 72.dp, modifier = Modifier.width(72.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(book.author, style = MaterialTheme.typography.bodySmall, color = BookStoreTheme.colors.textMuted, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            PriceTag(book)
            below()
        }
        trailing()
    }
}

/** A round placeholder for a seller without a picture. */
val AvatarShape = CircleShape
