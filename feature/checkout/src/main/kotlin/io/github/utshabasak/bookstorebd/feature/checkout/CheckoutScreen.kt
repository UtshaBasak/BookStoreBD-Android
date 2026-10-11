package io.github.utshabasak.bookstorebd.feature.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.ui.FormWidth
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.errorMessage
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatTaka
import io.github.utshabasak.bookstorebd.core.ui.onHardwareEnter
import io.github.utshabasak.bookstorebd.core.ui.readableWidth

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
) {
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
            Heading(R.string.feature_checkout_delivery)
            Field(
                state.form.name,
                { v -> onUpdate { it.copy(name = v) } },
                R.string.feature_checkout_name,
                FormProblem.Name in state.problems,
                R.string.feature_checkout_name_problem,
                KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            Field(
                state.form.phone,
                { v -> onUpdate { it.copy(phone = v) } },
                R.string.feature_checkout_phone,
                FormProblem.Phone in state.problems,
                R.string.feature_checkout_phone_problem,
                KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            )
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

            Heading(R.string.feature_checkout_promo)
            PromoRow(state, onUpdate, onApplyPromo, onRemovePromo)

            Heading(R.string.feature_checkout_summary)
            Summary(state)

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
                    Text(stringResource(R.string.feature_checkout_place_order, formatTaka(state.total, currentLocale())))
                }
            }
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
private fun PromoRow(
    state: CheckoutUiState,
    onUpdate: ((DeliveryForm) -> DeliveryForm) -> Unit,
    onApply: () -> Unit,
    onRemove: () -> Unit,
) {
    val promo = state.promo
    if (promo != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(promo.code, style = MaterialTheme.typography.titleSmall, color = BookStoreTheme.colors.success)
                Text(promo.description, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onRemove) { Text(stringResource(R.string.feature_checkout_remove_promo)) }
        }
        return
    }
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

@Composable
private fun Summary(state: CheckoutUiState) {
    val locale = currentLocale()
    val copies = state.items.sumOf { it.quantity }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SummaryLine(stringResource(R.string.feature_checkout_books, formatCount(copies, locale)), formatTaka(state.booksTotal, locale))
        if (state.discount > 0) SummaryLine(stringResource(R.string.feature_checkout_discount), "− " + formatTaka(state.discount, locale))
        SummaryLine(
            stringResource(R.string.feature_checkout_delivery_charge),
            if (state.form.district.isBlank()) {
                stringResource(
                    R.string.feature_checkout_choose_district,
                )
            } else {
                formatTaka(state.deliveryCharge, locale)
            },
        )
        HorizontalDivider()
        Row {
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
