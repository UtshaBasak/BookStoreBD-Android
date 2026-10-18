package io.github.utshabasak.bookstorebd.core.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * How many copies: minus, the number, plus, each button a 48dp target. To
 * TalkBack it is one adjustable control ("2 copies, up to 5"), changed with the
 * volume keys or a swipe, as a slider is.
 */
@Composable
fun QuantityStepper(
    value: Int,
    max: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val locale = currentLocale()
    val upper = max.coerceAtLeast(1)
    val description = pluralStringResource(R.plurals.core_ui_quantity, value, formatCount(value, locale), formatCount(upper, locale))
    Row(
        modifier =
            modifier
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 1f..upper.toFloat(), steps = (upper - 2).coerceAtLeast(0))
                    // While a change is being saved the buttons wait, and so does TalkBack's adjusting.
                    if (enabled) {
                        setProgress { target ->
                            val next = target.roundToInt().coerceIn(1, upper)
                            if (next != value) onChange(next)
                            true
                        }
                    } else {
                        disabled()
                    }
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onChange(value - 1) }, enabled = enabled && value > 1) {
            Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.core_ui_one_fewer))
        }
        Text(
            text = formatCount(value, locale),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 24.dp),
        )
        IconButton(onClick = { onChange(value + 1) }, enabled = enabled && value < upper) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.core_ui_one_more))
        }
    }
}
