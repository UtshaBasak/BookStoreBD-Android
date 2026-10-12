package io.github.utshabasak.bookstorebd.feature.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount

/** The three steps of buying, as the website's checkout shows them. */
enum class CheckoutStep(
    val label: Int,
) {
    Cart(R.string.feature_checkout_step_cart),
    Details(R.string.feature_checkout_step_details),
    Done(R.string.feature_checkout_step_done),
}

/** Cart, Details, Done: how far through checkout this is. */
@Composable
fun CheckoutSteps(
    current: CheckoutStep,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val spoken =
        stringResource(
            R.string.feature_checkout_step_of,
            formatCount(current.ordinal + 1, locale),
            formatCount(CheckoutStep.entries.size, locale),
            stringResource(current.label),
        )
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CheckoutStep.entries.forEach { step ->
            val done = step.ordinal < current.ordinal || current == CheckoutStep.Done
            val here = step == current
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            done -> BookStoreTheme.colors.success
                            here -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        formatCount(step.ordinal + 1, locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (here) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                stringResource(step.label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (here) FontWeight.Bold else FontWeight.Normal,
            )
            if (step != CheckoutStep.entries.last()) HorizontalDivider(Modifier.weight(1f).padding(horizontal = 4.dp))
        }
    }
}
