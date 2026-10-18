package io.github.utshabasak.bookstorebd.feature.checkout

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.utshabasak.bookstorebd.core.model.UnavailableItem
import io.github.utshabasak.bookstorebd.core.model.UnavailableReason
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.navigation.LeftOutBook

/** A book the shop left out, as the confirmation route carries it. */
internal fun UnavailableItem.toLeftOut(): LeftOutBook = LeftOutBook(title, reason.name, requested, reserved)

/** "Title (why)", for each book left out or short, as the website writes it. */
@Composable
internal fun leftOutList(books: List<LeftOutBook>): String {
    val locale = currentLocale()
    val unnamed = stringResource(R.string.feature_checkout_a_book)
    return books
        .map { book ->
            val why =
                when (UnavailableReason.entries.firstOrNull { it.name == book.reason } ?: UnavailableReason.SoldOut) {
                    UnavailableReason.Short -> {
                        stringResource(
                            R.string.feature_checkout_reason_short,
                            formatCount(book.reserved, locale),
                            formatCount(book.requested, locale),
                        )
                    }

                    UnavailableReason.Removed -> {
                        stringResource(R.string.feature_checkout_reason_removed)
                    }

                    UnavailableReason.OwnListing -> {
                        stringResource(R.string.feature_checkout_reason_own)
                    }

                    UnavailableReason.SellerUnavailable -> {
                        stringResource(R.string.feature_checkout_reason_seller)
                    }

                    UnavailableReason.SoldOut -> {
                        stringResource(R.string.feature_checkout_reason_sold_out)
                    }
                }
            stringResource(R.string.feature_checkout_left_out_book, book.title.ifBlank { unnamed }, why)
        }.joinToString(", ")
}
