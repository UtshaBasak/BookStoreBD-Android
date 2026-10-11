package io.github.utshabasak.bookstorebd.feature.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import io.github.utshabasak.bookstorebd.core.model.AppLanguage
import io.github.utshabasak.bookstorebd.core.model.SetupItem
import io.github.utshabasak.bookstorebd.core.model.ThemePreference
import io.github.utshabasak.bookstorebd.core.ui.errorMessage

/** Opens a page of the website in a Custom Tab: the browser, styled for the app, with the person's own cookies. */
fun openPage(
    context: Context,
    url: String,
) {
    if (url.isBlank()) return
    CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, url.toUri())
}

/** The language Android keeps for this app (per-app language), or the phone's. */
fun currentAppLanguage(): AppLanguage {
    val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    return AppLanguage.entries.firstOrNull { language -> language.tag?.let(tag::startsWith) == true } ?: AppLanguage.System
}

fun setAppLanguage(language: AppLanguage) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag.orEmpty()))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    state: AccountUiState,
    update: UpdateUiState,
    actions: AccountActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    language: AppLanguage = AppLanguage.System,
    inbox: InboxCounts = InboxCounts(),
) {
    var confirmSignOutOthers by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            val user = state.user
            if (user != null) {
                item { ProfileHeader(state.profile, user.username, user.email) { actions.onEditProfile(null) } }
                state.profile?.let { profile -> item { SetupCard(profile) { step -> actions.onSetupStep(step) } } }
                item { Shortcuts(actions, inbox) }
                item { Section(R.string.feature_settings_selling) }
                if (state.profile?.canSell == false) {
                    item {
                        LinkRow(
                            Icons.Outlined.Payments,
                            R.string.feature_settings_bkash_to_sell,
                            external = false,
                            onClick = actions.onAddBkash,
                        )
                    }
                }
                item { LinkRow(Icons.Outlined.AddBox, R.string.feature_settings_list_book, external = false, onClick = actions.onListBook) }
                item {
                    LinkRow(
                        Icons.Outlined.Inventory2,
                        R.string.feature_settings_my_books,
                        external = false,
                        onClick = actions.onMyBooks,
                    )
                }
                item {
                    LinkRow(
                        Icons.AutoMirrored.Outlined.ReceiptLong,
                        R.string.feature_settings_sales,
                        external = false,
                        onClick = actions.onSales,
                    )
                }
                item { Section(R.string.feature_settings_security) }
                item {
                    LinkRow(Icons.Outlined.Edit, R.string.feature_settings_edit_profile, external = false) { actions.onEditProfile(null) }
                }
                state.profile?.let { profile ->
                    item {
                        TwoStepRow(
                            enabled = profile.twoFactor,
                            busy = state.twoStepBusy,
                            problem = state.twoStepProblem,
                            onChange = actions.onTwoStep,
                            onDismissProblem = actions.onDismissTwoStepProblem,
                        )
                    }
                }
                item {
                    LinkRow(Icons.Outlined.Devices, R.string.feature_settings_sign_out_others, external = false) {
                        confirmSignOutOthers = true
                    }
                }
            } else {
                item { Profile(state, actions) }
            }
            item { Section(R.string.feature_settings_appearance) }
            item {
                ChoiceGroup(
                    options = ThemePreference.entries.map { it to themeLabel(it) },
                    selected = state.theme,
                    onSelect = actions.onTheme,
                )
            }
            item { Section(R.string.feature_settings_language) }
            item {
                ChoiceGroup(
                    options = AppLanguage.entries.map { it to languageLabel(it) },
                    selected = language,
                    onSelect = actions.onLanguage,
                )
            }
            item { Section(R.string.feature_settings_app) }
            item { LinkRow(Icons.Outlined.Lock, R.string.feature_settings_privacy_data, external = false, onClick = actions.onPrivacy) }
            item { UpdateRow(update, actions) }
            state.links?.let { links ->
                item {
                    LinkRow(Icons.AutoMirrored.Outlined.HelpOutline, R.string.feature_settings_how_it_works) {
                        actions.onOpen(links.howItWorks)
                    }
                }
                item { LinkRow(Icons.Outlined.Replay, R.string.feature_settings_returns) { actions.onOpen(links.returns) } }
                item { LinkRow(Icons.Outlined.Policy, R.string.feature_settings_privacy) { actions.onOpen(links.privacy) } }
                item { LinkRow(Icons.Outlined.Info, R.string.feature_settings_terms) { actions.onOpen(links.terms) } }
            }
            if (state.user != null) {
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item {
                    LinkRow(
                        Icons.AutoMirrored.Outlined.Logout,
                        R.string.feature_settings_sign_out,
                        external = false,
                        onClick = actions.onSignOut,
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.feature_settings_version, update.currentVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
    if (confirmSignOutOthers) {
        AlertDialog(
            onDismissRequest = { confirmSignOutOthers = false },
            title = { Text(stringResource(R.string.feature_settings_sign_out_others)) },
            text = { Text(stringResource(R.string.feature_settings_sign_out_others_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOutOthers = false
                    actions.onSignOutOthers()
                }) { Text(stringResource(R.string.feature_settings_sign_out_others_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOutOthers = false }) { Text(stringResource(R.string.feature_settings_cancel)) }
            },
        )
    }
}

data class AccountActions(
    val onSignIn: () -> Unit,
    val onCreateAccount: () -> Unit,
    val onSignOut: () -> Unit,
    val onSignOutOthers: () -> Unit,
    val onTheme: (ThemePreference) -> Unit,
    val onLanguage: (AppLanguage) -> Unit,
    val onCheckUpdates: () -> Unit,
    val onDownload: (io.github.utshabasak.bookstorebd.core.model.AppRelease) -> Unit,
    val onOpen: (String) -> Unit,
    val onEditProfile: (SetupItem?) -> Unit = {},
    val onSetupStep: (SetupItem) -> Unit = {},
    val onOrders: () -> Unit = {},
    val onWishlist: () -> Unit = {},
    val onWanted: () -> Unit = {},
    val onInvite: () -> Unit = {},
    val onMessages: () -> Unit = {},
    val onListBook: () -> Unit = {},
    val onMyBooks: () -> Unit = {},
    val onSales: () -> Unit = {},
    val onAddBkash: () -> Unit = {},
    val onNotifications: () -> Unit = {},
    val onPrivacy: () -> Unit = {},
    val onTwoStep: (Boolean, String?) -> Unit = { _, _ -> },
    val onDismissTwoStepProblem: () -> Unit = {},
)

@Composable
private fun Profile(
    state: AccountUiState,
    actions: AccountActions,
) {
    val user = state.user
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.AccountCircle,
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    user?.username ?: stringResource(R.string.feature_settings_signed_out),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    user?.email ?: stringResource(R.string.feature_settings_signed_out_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (user == null && state.sessionKnown) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = actions.onSignIn, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.feature_settings_sign_in))
                }
                OutlinedButton(onClick = actions.onCreateAccount, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.feature_settings_create_account))
                }
            }
        }
    }
}

@Composable
private fun Section(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
    )
}

/** Radio buttons, announced as one group, each row a full-width target. */
@Composable
private fun <T> ChoiceGroup(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        options.forEach { (value, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = value == selected, onClick = null)
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun UpdateRow(
    update: UpdateUiState,
    actions: AccountActions,
) {
    val newer = update.newer
    val supporting =
        when (val status = update.status) {
            UpdateStatus.Checking -> {
                stringResource(R.string.feature_settings_update_checking)
            }

            UpdateStatus.UpToDate -> {
                stringResource(R.string.feature_settings_update_latest)
            }

            is UpdateStatus.Downloading -> {
                stringResource(R.string.feature_settings_update_downloading, (status.progress * PERCENT).toInt())
            }

            is UpdateStatus.Ready -> {
                stringResource(R.string.feature_settings_update_ready)
            }

            is UpdateStatus.Failed -> {
                errorMessage(status.error)
            }

            UpdateStatus.Idle -> {
                if (newer !=
                    null
                ) {
                    stringResource(R.string.feature_settings_update_available, newer.versionName)
                } else {
                    stringResource(R.string.feature_settings_update_tap)
                }
            }
        }
    ListItem(
        headlineContent = { Text(stringResource(R.string.feature_settings_updates)) },
        supportingContent = { Text(supporting) },
        leadingContent = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
        trailingContent = {
            when {
                newer != null && update.canInstall -> {
                    TextButton(onClick = {
                        actions.onDownload(newer)
                    }) { Text(stringResource(R.string.feature_settings_update_install)) }
                }

                newer != null -> {
                    TextButton(onClick = {
                        actions.onOpen(newer.pageUrl)
                    }) { Text(stringResource(R.string.feature_settings_update_open)) }
                }

                else -> {
                    TextButton(onClick = actions.onCheckUpdates) { Text(stringResource(R.string.feature_settings_update_check)) }
                }
            }
        },
    )
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    title: Int,
    external: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = if (external) ({ Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) }) else null,
        modifier = Modifier.selectable(selected = false, role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun themeLabel(theme: ThemePreference): String =
    stringResource(
        when (theme) {
            ThemePreference.System -> R.string.feature_settings_theme_system
            ThemePreference.Light -> R.string.feature_settings_theme_light
            ThemePreference.Dark -> R.string.feature_settings_theme_dark
        },
    )

@Composable
private fun languageLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.System -> stringResource(R.string.feature_settings_language_system)

        // Each language named in itself, so it can be found whatever the app is in.
        AppLanguage.English -> "English"

        AppLanguage.Bangla -> "বাংলা"
    }

private const val PERCENT = 100
