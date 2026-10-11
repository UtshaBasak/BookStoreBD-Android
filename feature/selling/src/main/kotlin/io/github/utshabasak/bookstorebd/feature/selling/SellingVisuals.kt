package io.github.utshabasak.bookstorebd.feature.selling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.Discount
import io.github.utshabasak.bookstorebd.core.model.DiscountKind
import io.github.utshabasak.bookstorebd.core.model.DiscountProblem
import io.github.utshabasak.bookstorebd.core.model.OrderStatus
import io.github.utshabasak.bookstorebd.core.model.PayoutState
import io.github.utshabasak.bookstorebd.core.model.Pricing
import io.github.utshabasak.bookstorebd.core.model.Sale
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatDate
import io.github.utshabasak.bookstorebd.core.ui.formatTaka

@Composable
internal fun statusText(status: OrderStatus): String = stringResource(statusRes(status))

internal fun statusRes(status: OrderStatus): Int =
    when (status) {
        OrderStatus.Confirmed -> R.string.feature_selling_status_confirmed
        OrderStatus.Processing -> R.string.feature_selling_status_processing
        OrderStatus.Shipped -> R.string.feature_selling_status_shipped
        OrderStatus.OutForDelivery -> R.string.feature_selling_status_out
        OrderStatus.Delivered -> R.string.feature_selling_status_delivered
        OrderStatus.Cancelled -> R.string.feature_selling_status_cancelled
    }

/** Where the seller's money for a line has got to, as the website says it. */
@Composable
internal fun payoutText(sale: Sale): String =
    when {
        sale.line.isCancelled -> {
            stringResource(R.string.feature_selling_payout_cancelled)
        }

        else -> {
            when (sale.payout) {
                PayoutState.Paid -> {
                    stringResource(R.string.feature_selling_payout_paid, formatDate(sale.paidAt), sale.payoutRef.ifBlank { "-" })
                }

                PayoutState.Due -> {
                    stringResource(R.string.feature_selling_payout_due)
                }

                PayoutState.InReturnWindow -> {
                    stringResource(R.string.feature_selling_payout_from, formatDate(sale.payableFrom))
                }

                PayoutState.ReturnInProgress -> {
                    stringResource(R.string.feature_selling_payout_on_hold)
                }

                PayoutState.Returned -> {
                    stringResource(R.string.feature_selling_payout_returned)
                }

                PayoutState.AwaitingDelivery -> {
                    stringResource(R.string.feature_selling_payout_after_delivery)
                }
            }
        }
    }

@Composable
internal fun discountProblemText(problem: DiscountProblem): String =
    when (problem) {
        DiscountProblem.NotWhole -> stringResource(R.string.feature_selling_discount_whole)
        DiscountProblem.FreeBook -> stringResource(R.string.feature_selling_discount_free)
        DiscountProblem.PercentTooLarge -> stringResource(R.string.feature_selling_discount_percent_max, Pricing.MAX_DISCOUNT_PERCENT)
        DiscountProblem.NotBelowPrice -> stringResource(R.string.feature_selling_discount_below_price)
        DiscountProblem.AmountTooLarge -> stringResource(R.string.feature_selling_discount_amount_max, Pricing.MAX_DISCOUNT_PERCENT)
    }

/**
 * The website's discount editor: none, a percentage or an amount off, with
 * what the book will sell for, worked out by the same rules as the server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscountEditor(
    price: Int?,
    discount: Discount,
    onChange: (Discount) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val problem = price?.let { Pricing.problem(it, discount) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.feature_selling_discount), style = MaterialTheme.typography.titleSmall)
        val kinds = DiscountKind.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            kinds.forEachIndexed { index, kind ->
                SegmentedButton(
                    selected = discount.kind == kind,
                    onClick = { onChange(discount.copy(kind = kind)) },
                    shape = SegmentedButtonDefaults.itemShape(index, kinds.size),
                ) {
                    Text(
                        stringResource(
                            when (kind) {
                                DiscountKind.None -> R.string.feature_selling_discount_none
                                DiscountKind.Percent -> R.string.feature_selling_discount_percent
                                DiscountKind.Amount -> R.string.feature_selling_discount_amount
                            },
                        ),
                    )
                }
            }
        }
        if (discount.kind != DiscountKind.None) {
            OutlinedTextField(
                value = if (discount.value == 0) "" else discount.value.toString(),
                onValueChange = { text -> onChange(discount.copy(value = text.filter(Char::isDigit).take(MAX_DIGITS).toIntOrNull() ?: 0)) },
                label = {
                    Text(
                        stringResource(
                            if (discount.kind ==
                                DiscountKind.Percent
                            ) {
                                R.string.feature_selling_discount_percent_value
                            } else {
                                R.string.feature_selling_discount_amount_value
                            },
                        ),
                    )
                },
                isError = problem != null,
                supportingText = {
                    when {
                        problem != null -> {
                            Text(discountProblemText(problem))
                        }

                        price != null -> {
                            Text(
                                stringResource(
                                    R.string.feature_selling_sells_for,
                                    formatTaka(Pricing.salePrice(price, discount).toDouble(), locale),
                                ),
                                color = BookStoreTheme.colors.success,
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val MAX_DIGITS = 6
