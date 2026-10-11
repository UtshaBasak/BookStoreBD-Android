package io.github.utshabasak.bookstorebd.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.utshabasak.bookstorebd.core.designsystem.theme.brandGradient
import io.github.utshabasak.bookstorebd.core.model.Gender
import io.github.utshabasak.bookstorebd.core.model.SetupItem
import io.github.utshabasak.bookstorebd.core.ui.FormWidth
import io.github.utshabasak.bookstorebd.core.ui.PasswordChecklist
import io.github.utshabasak.bookstorebd.core.ui.SkeletonBlock
import io.github.utshabasak.bookstorebd.core.ui.SkeletonScreen
import io.github.utshabasak.bookstorebd.core.ui.readableWidth
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** What the screen can ask of its view model. */
data class EditProfileActions(
    val onBack: () -> Unit,
    val onEdit: ((ProfileForm) -> ProfileForm) -> Unit,
    val onPickPicture: (PictureSlot) -> Unit,
    val onRemovePicture: (PictureSlot) -> Unit,
    val onSave: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    state: EditProfileUiState,
    actions: EditProfileActions,
    focus: SetupItem?,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val leave = { if (state.hasChanges && !state.saving) confirmDiscard = true else actions.onBack() }
    BackHandler(enabled = state.hasChanges && !state.saving) { confirmDiscard = true }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_profile_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_profile_back))
                    }
                },
                actions = {
                    TextButton(onClick = actions.onSave, enabled = state.loaded && !state.saving) {
                        Text(stringResource(R.string.feature_profile_save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.loaded) {
            SkeletonScreen(Modifier.padding(padding)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SkeletonBlock(Modifier.size(96.dp), CircleShape)
                    repeat(FIELD_PLACEHOLDERS) { SkeletonBlock(Modifier.fillMaxWidth().heightIn(min = 56.dp), RoundedCornerShape(8.dp)) }
                }
            }
            return@Scaffold
        }
        val list = rememberLazyListState()
        LaunchedEffect(focus) {
            val index =
                when (focus) {
                    SetupItem.Photo, SetupItem.Banner -> 0
                    SetupItem.DateOfBirth -> SECTION_ABOUT
                    SetupItem.Phone, SetupItem.Address -> SECTION_CONTACT
                    SetupItem.Bkash -> SECTION_PAYMENT
                    else -> null
                }
            if (index != null) list.animateScrollToItem(index)
        }
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .readableWidth(FormWidth),
        ) {
            item { Pictures(state, actions) }
            item { AccountSection(state, actions) }
            item { PasswordSection(state, actions) }
            item { AboutSection(state, actions) }
            item { ContactSection(state, actions) }
            item { PaymentSection(state, actions) }
            item {
                Button(
                    onClick = actions.onSave,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 8.dp),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.feature_profile_save_changes))
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.feature_profile_discard_title)) },
            text = { Text(stringResource(R.string.feature_profile_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    actions.onBack()
                }) { Text(stringResource(R.string.feature_profile_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.feature_profile_keep_editing)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp).semantics { heading() },
    )
}

@Composable
private fun Pictures(
    state: EditProfileUiState,
    actions: EditProfileActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(R.string.feature_profile_pictures)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val shown = state.picture.shownUrl
                if (shown != null) {
                    AsyncImage(
                        model = shown,
                        contentDescription = stringResource(R.string.feature_profile_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { actions.onPickPicture(PictureSlot.Photo) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(
                        stringResource(
                            if (state.picture.hasPicture) R.string.feature_profile_change_photo else R.string.feature_profile_add_photo,
                        ),
                    )
                }
                if (state.picture.hasPicture) {
                    TextButton(onClick = {
                        actions.onRemovePicture(PictureSlot.Photo)
                    }) { Text(stringResource(R.string.feature_profile_remove)) }
                }
            }
        }
        if (state.preparingPicture) {
            Text(stringResource(R.string.feature_profile_preparing), style = MaterialTheme.typography.bodySmall)
        }
        Banner(R.string.feature_profile_buyer_banner, state.buyerBanner, PictureSlot.BuyerBanner, actions)
        Banner(R.string.feature_profile_seller_banner, state.sellerBanner, PictureSlot.SellerBanner, actions)
    }
}

@Composable
private fun Banner(
    label: Int,
    picture: PictureState,
    slot: PictureSlot,
    actions: EditProfileActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(BANNER_RATIO)
                .clip(RoundedCornerShape(12.dp))
                .background(brandGradient()),
        ) {
            picture.shownUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = stringResource(label),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { actions.onPickPicture(slot) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (picture.hasPicture) R.string.feature_profile_change else R.string.feature_profile_add))
            }
            if (picture.hasPicture) {
                TextButton(onClick = { actions.onRemovePicture(slot) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.feature_profile_remove))
                }
            }
        }
    }
}

@Composable
private fun AccountSection(
    state: EditProfileUiState,
    actions: EditProfileActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(R.string.feature_profile_account)
        FormTextField(
            value = state.form.username,
            onChange = { v -> actions.onEdit { it.copy(username = v) } },
            label = R.string.feature_profile_username,
            problem = state.problems.problem(FormField.Username, R.string.feature_profile_username_problem),
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(stringResource(R.string.feature_profile_email)) },
            supportingText = { Text(stringResource(R.string.feature_profile_email_fixed)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PasswordSection(
    state: EditProfileUiState,
    actions: EditProfileActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(R.string.feature_profile_password)
        FormTextField(
            value = state.form.newPassword,
            onChange = { v -> actions.onEdit { it.copy(newPassword = v) } },
            label = if (state.passwordSet) R.string.feature_profile_new_password else R.string.feature_profile_choose_password,
            problem = state.problems.problem(FormField.NewPassword, R.string.feature_profile_password_problem),
            password = true,
            supporting = R.string.feature_profile_password_blank,
        )
        if (state.form.newPassword.isNotEmpty()) PasswordChecklist(state.passwordCheck)
        if (state.needsCurrentPassword) {
            Text(stringResource(R.string.feature_profile_confirm_title), style = MaterialTheme.typography.titleSmall)
            FormTextField(
                value = state.form.currentPassword,
                onChange = { v -> actions.onEdit { it.copy(currentPassword = v) } },
                label = R.string.feature_profile_current_password,
                problem = state.problems.problem(FormField.CurrentPassword, R.string.feature_profile_current_password_problem),
                password = true,
                supporting = R.string.feature_profile_confirm_body,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSection(
    state: EditProfileUiState,
    actions: EditProfileActions,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(R.string.feature_profile_about)
        OutlinedTextField(
            value = state.form.dateOfBirth,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.feature_profile_birthday)) },
            trailingIcon = {
                IconButton(onClick = { picking = true }) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(R.string.feature_profile_pick_birthday))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.form.dateOfBirth.isNotEmpty()) {
            TextButton(onClick = {
                actions.onEdit { it.copy(dateOfBirth = "") }
            }) { Text(stringResource(R.string.feature_profile_clear_birthday)) }
        }
        GenderPicker(state.form.gender) { gender -> actions.onEdit { it.copy(gender = gender) } }
    }
    if (picking) {
        val initial =
            runCatching {
                LocalDate.parse(state.form.dateOfBirth).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            }.getOrNull()
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        actions.onEdit { it.copy(dateOfBirth = date) }
                    }
                    picking = false
                }) { Text(stringResource(R.string.feature_profile_ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.feature_profile_cancel)) } },
        ) { DatePicker(state = picker) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderPicker(
    gender: Gender?,
    onPick: (Gender?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val options =
        listOf(
            null to R.string.feature_profile_gender_none,
            Gender.Male to R.string.feature_profile_gender_male,
            Gender.Female to R.string.feature_profile_gender_female,
        )
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = stringResource(options.first { it.first == gender }.second),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.feature_profile_gender)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                    onPick(value)
                    open = false
                })
            }
        }
    }
}

@Composable
private fun ContactSection(
    state: EditProfileUiState,
    actions: EditProfileActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(R.string.feature_profile_contact)
        FormTextField(
            value = state.form.phone,
            onChange = { v -> actions.onEdit { it.copy(phone = v) } },
            label = R.string.feature_profile_phone,
            problem = state.problems.problem(FormField.Phone, R.string.feature_profile_phone_problem),
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        FormTextField(
            value = state.form.address,
            onChange = { v -> actions.onEdit { it.copy(address = v) } },
            label = R.string.feature_profile_address,
            problem = null,
            singleLine = false,
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

@Composable
private fun PaymentSection(
    state: EditProfileUiState,
    actions: EditProfileActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(R.string.feature_profile_payment)
        FormTextField(
            value = state.form.bkash,
            onChange = { v -> actions.onEdit { it.copy(bkash = v) } },
            label = R.string.feature_profile_bkash,
            problem = state.problems.problem(FormField.Bkash, R.string.feature_profile_bkash_problem),
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone),
            supporting = R.string.feature_profile_bkash_help,
        )
    }
}

@Composable
private fun Map<FormField, String?>.problem(
    field: FormField,
    fallback: Int,
): String? = if (field in this) this[field] ?: stringResource(fallback) else null

@Composable
private fun FormTextField(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    problem: String?,
    password: Boolean = false,
    singleLine: Boolean = true,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    supporting: Int? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = problem != null,
        supportingText = (problem ?: supporting?.let { stringResource(it) })?.let { text -> { Text(text) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (password) KeyboardOptions(keyboardType = KeyboardType.Password) else keyboard,
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val BANNER_RATIO = 3f
private const val FIELD_PLACEHOLDERS = 5
private const val SECTION_ABOUT = 3
private const val SECTION_CONTACT = 4
private const val SECTION_PAYMENT = 5
