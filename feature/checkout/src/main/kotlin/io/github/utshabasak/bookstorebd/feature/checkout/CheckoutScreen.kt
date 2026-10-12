package io.github.utshabasak.bookstorebd.feature.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.CartItem
import io.github.utshabasak.bookstorebd.core.ui.BookCover
import io.github.utshabasak.bookstorebd.core.ui.FormWidth
import io.github.utshabasak.bookstorebd.core.ui.QuantityStepper
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.errorMessage
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatTaka
import io.github.utshabasak.bookstorebd.core.ui.onHardwareEnter
import io.github.utshabasak.bookstorebd.core.ui.readableWidth

/** What checkout's book list can do: change a quantity, or take a book out. */
data class CheckoutCartActions(
    val onQuantity: (CartItem, Int) -> Unit = { _, _ -> },
    val onRemove: (CartItem) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    state: CheckoutUiState,
    onUpdate: ((DeliveryForm) -> DeliveryForm) -> Unit,
    onApplyPromo: () -> Unit,
    onRemovePromo: () -> Unit,
    onPlaceOrder: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    cartActions: CheckoutCartActions = CheckoutCartActions(),
) {
    val locale = currentLocale()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_checkout_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_checkout_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .readableWidth(FormWidth)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CheckoutSteps(current = CheckoutStep.Details)

            // The basket first, as on the website: what is bought, changed in place, and what it costs.
            Heading(R.string.feature_checkout_your_order)
            YourOrder(state, cartActions)
            Summary(state)
            PromoBox(state, onUpdate, onApplyPromo, onRemovePromo)
            Total(state)

            Heading(R.string.feature_checkout_contact)
            Field(
                state.form.name,
                { v -> onUpdate { it.copy(name = v) } },
                R.string.feature_checkout_name,
                FormProblem.Name in state.problems,
                R.string.feature_checkout_name_problem,
                KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            Field(
                state.form.email,
                { v -> onUpdate { it.copy(email = v.trim()) } },
                R.string.feature_checkout_email,
                FormProblem.Email in state.problems,
                R.string.feature_checkout_email_problem,
                KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            Field(
                state.form.phone,
                { v -> onUpdate { it.copy(phone = v) } },
                R.string.feature_checkout_phone,
                FormProblem.Phone in state.problems,
                R.string.feature_checkout_phone_problem,
                KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            )

            Heading(R.string.feature_checkout_delivery)
            Picker(
                label = R.string.feature_checkout_division,
                value = state.form.division,
                options = state.divisions.map { it.name },
                problem = FormProblem.Division in state.problems,
                onPick = { v -> onUpdate { it.copy(division = v) } },
            )
            Picker(
                label = R.string.feature_checkout_district,
                value = state.form.district,
                options = state.districts,
                problem = FormProblem.District in state.problems,
                onPick = { v -> onUpdate { it.copy(district = v) } },
            )
            Field(
                state.form.address,
                { v -> onUpdate { it.copy(address = v) } },
                R.string.feature_checkout_address,
                FormProblem.Address in state.problems,
                R.string.feature_checkout_address_problem,
                KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                singleLine = false,
            )
            OutlinedTextField(
                value = state.form.note,
                onValueChange = { v -> onUpdate { it.copy(note = v.take(NOTE_LIMIT)) } },
                label = { Text(stringResource(R.string.feature_checkout_note)) },
                supportingText = { Text(stringResource(R.string.feature_checkout_note_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.feature_checkout_cash_on_delivery), style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = onPlaceOrder,
                enabled = !state.placing && state.items.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                if (state.placing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.feature_checkout_place_order, formatTaka(state.total, locale)))
                }
            }
            state.blocked?.let { block ->
                Text(
                    stringResource(
                        when (block) {
                            CheckoutBlock.PromoStale -> R.string.feature_checkout_promo_stale_blocked
                            CheckoutBlock.NothingInStock -> R.string.feature_checkout_nothing_in_stock
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (state.returnWindowDays > 0) {
                Text(
                    stringResource(R.string.feature_checkout_returns_note, formatCount(state.returnWindowDays, locale)),
                    style = MaterialTheme.typography.bodySmall,
                    color = BookStoreTheme.colors.textMuted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The books being ordered: each with its quantity, changed in place, and a way to take it out. */
@Composable
private fun YourOrder(
    state: CheckoutUiState,
    actions: CheckoutCartActions,
) {
    val locale = currentLocale()
    if (state.soldOut.isNotEmpty()) {
        Text(
            stringResource(R.string.feature_checkout_sold_out_left_out, state.soldOut.joinToString(", ") { it.book.title }),
            style = MaterialTheme.typography.bodyMedium,
            color = BookStoreTheme.colors.warning,
        )
    }
    if (state.items.isEmpty()) {
        Text(
            stringResource(
                if (state.soldOut.isNotEmpty()) R.string.feature_checkout_nothing_in_stock else R.string.feature_checkout_no_books,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = BookStoreTheme.colors.textMuted,
        )
        return
    }
    Column {
        state.items.forEach { item ->
            val busy = state.busyBookId == item.book.id
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                BookCover(url = item.book.cover, drawnWidth = 56.dp, modifier = Modifier.width(56.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(item.book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        item.book.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = BookStoreTheme.colors.textMuted,
                        maxLines = 1,
                    )
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        QuantityStepper(
                            value = item.quantity,
                            max = item.book.stock,
                            onChange = { actions.onQuantity(item, it) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            formatTaka(item.lineTotal, locale),
                            style = MaterialTheme.typography.titleSmall,
                            color = BookStoreTheme.colors.price,
                        )
                    }
                    if (item.book.stock <= LOW_STOCK) {
                        Text(
                            pluralStringResource(
                                R.plurals.feature_checkout_only_left,
                                item.book.stock,
                                formatCount(item.book.stock, locale),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = BookStoreTheme.colors.warning,
                        )
                    }
                }
                IconButton(onClick = { actions.onRemove(item) }, enabled = !busy) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.feature_checkout_remove_book, item.book.title))
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun Heading(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        modifier =
            Modifier.padding(top = 8.dp).semantics {
                heading()
            },
    )
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    problem: Boolean,
    problemText: Int,
    keyboard: KeyboardOptions,
    singleLine: Boolean = true,
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = problem,
        supportingText = if (problem) ({ Text(stringResource(problemText)) }) else null,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = keyboard,
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (singleLine) Modifier.onHardwareEnter { focus.moveFocus(FocusDirection.Down) } else Modifier),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(
    label: Int,
    value: String,
    options: List<String>,
    problem: Boolean,
    onPick: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it && options.isNotEmpty() }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(label)) },
            isError = problem,
            supportingText = if (problem) ({ Text(stringResource(R.string.feature_checkout_choose_one)) }) else null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = options.isNotEmpty()),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    onPick(option)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun PromoBox(
    state: CheckoutUiState,
    onUpdate: ((DeliveryForm) -> DeliveryForm) -> Unit,
    onApply: () -> Unit,
    onRemove: () -> Unit,
) {
    val locale = currentLocale()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading(R.string.feature_checkout_promo)
        // The moment it applies, not buried in the terms:
        // a code nobody hears about gives nobody free delivery.
        val free = state.promotions.freeDelivery
        if (state.couldHaveFreeDelivery && free != null) {
            val tip =
                if (state.promoCurrent) R.string.feature_checkout_free_delivery_instead else R.string.feature_checkout_free_delivery_tip
            Text(
                stringResource(
                    tip,
                    formatTaka(free.minBooksTotal.toDouble(), locale),
                    free.code,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = BookStoreTheme.colors.success,
            )
        }
        val promo = state.promo
        if (promo != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(promo.code, style = MaterialTheme.typography.titleSmall, color = BookStoreTheme.colors.success)
                    Text(promo.description, style = MaterialTheme.typography.bodySmall)
                }
                if (state.promoStale) TextButton(onClick = onApply) { Text(stringResource(R.string.feature_checkout_apply)) }
                TextButton(onClick = onRemove) { Text(stringResource(R.string.feature_checkout_remove_promo)) }
            }
            if (state.promoStale) {
                Text(
                    stringResource(R.string.feature_checkout_promo_stale),
                    style = MaterialTheme.typography.bodySmall,
                    color = BookStoreTheme.colors.warning,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.form.promoCode,
                    onValueChange = { v -> onUpdate { it.copy(promoCode = v.take(PROMO_LIMIT)) } },
                    label = { Text(stringResource(R.string.feature_checkout_promo_code)) },
                    isError = state.promoError != null,
                    supportingText = state.promoError?.let { error -> { Text(errorMessage(error)) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onApply,
                    enabled = !state.checkingPromo && state.form.promoCode.isNotBlank(),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.feature_checkout_apply))
                }
            }
        }
        state.promotions.firstOrder?.let { first ->
            Text(
                stringResource(R.string.feature_checkout_first_order, first.code, first.description),
                style = MaterialTheme.typography.bodySmall,
                color = BookStoreTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun Summary(state: CheckoutUiState) {
    val locale = currentLocale()
    val copies = state.items.sumOf { it.quantity }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SummaryLine(stringResource(R.string.feature_checkout_books, formatCount(copies, locale)), formatTaka(state.booksTotal, locale))
        SummaryLine(
            stringResource(R.string.feature_checkout_delivery_charge),
            when {
                state.freeDelivery -> stringResource(R.string.feature_checkout_free)
                state.form.district.isBlank() -> stringResource(R.string.feature_checkout_choose_district)
                state.deliveryCharge == 0.0 -> stringResource(R.string.feature_checkout_free)
                else -> formatTaka(state.deliveryCharge, locale)
            },
        )
        state.delivery?.let { rules ->
            val free = state.promotions.freeDelivery
            val charges =
                stringResource(
                    R.string.feature_checkout_delivery_rules,
                    formatTaka(rules.insideDhaka.toDouble(), locale),
                    formatTaka(rules.outsideDhaka.toDouble(), locale),
                )
            val freeWith =
                free?.let {
                    " " +
                        stringResource(
                            R.string.feature_checkout_delivery_free_with,
                            it.code,
                            formatTaka(it.minBooksTotal.toDouble(), locale),
                        )
                }.orEmpty()
            val days =
                " " +
                    stringResource(
                        R.string.feature_checkout_delivery_days,
                        formatCount(rules.daysInsideDhaka, locale),
                        formatCount(rules.daysOutsideDhaka, locale),
                    )
            Text(charges + freeWith + days, style = MaterialTheme.typography.bodySmall, color = BookStoreTheme.colors.textMuted)
        }
        if (state.discount > 0) SummaryLine(stringResource(R.string.feature_checkout_discount), "− " + formatTaka(state.discount, locale))
    }
}

@Composable
private fun Total(state: CheckoutUiState) {
    val locale = currentLocale()
    Column {
        HorizontalDivider()
        Row(Modifier.padding(top = 8.dp).semantics(mergeDescendants = true) {}) {
            Text(
                stringResource(R.string.feature_checkout_total),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatTaka(state.total, locale),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = BookStoreTheme.colors.price,
            )
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
) {
    Row(Modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private const val NOTE_LIMIT = 300
private const val PROMO_LIMIT = 40

/** "Only N left" from this many copies down. */
private const val LOW_STOCK = 5
