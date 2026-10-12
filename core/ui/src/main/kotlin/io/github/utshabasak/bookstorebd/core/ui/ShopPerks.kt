package io.github.utshabasak.bookstorebd.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.DeliveryRules

/**
 * The shop's promises, as the website lists them on its home page, book pages
 * and cart: cash on delivery, the return window, and delivery. [days] says how
 * long delivery takes (the cart); otherwise the lowest charge is given.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShopPerks(
    returnDays: Int,
    delivery: DeliveryRules?,
    modifier: Modifier = Modifier,
    days: Boolean = false,
) {
    val locale = currentLocale()
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Perk(Icons.Outlined.Payments, stringResource(if (days) R.string.core_ui_perk_cod_arrives else R.string.core_ui_perk_cod))
        if (returnDays > 0) Perk(Icons.Outlined.Replay, stringResource(R.string.core_ui_perk_returns, formatCount(returnDays, locale)))
        delivery?.let { rules ->
            val text =
                if (days) {
                    stringResource(
                        R.string.core_ui_perk_delivery_days,
                        formatCount(rules.daysInsideDhaka, locale),
                        formatCount(rules.daysOutsideDhaka, locale),
                    )
                } else {
                    stringResource(R.string.core_ui_perk_delivery_from, formatTaka(rules.insideDhaka.toDouble(), locale))
                }
            Perk(Icons.Outlined.LocalShipping, text)
        }
    }
}

@Composable
private fun Perk(
    icon: ImageVector,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = BookStoreTheme.colors.textMuted,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
