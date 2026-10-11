package io.github.utshabasak.bookstorebd.feature.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.NotificationCategory
import io.github.utshabasak.bookstorebd.core.model.NotificationChoice
import io.github.utshabasak.bookstorebd.core.ui.ErrorState
import io.github.utshabasak.bookstorebd.core.ui.SkeletonBlock
import io.github.utshabasak.bookstorebd.core.ui.SkeletonScreen
import io.github.utshabasak.bookstorebd.core.ui.readableWidth

data class NotificationSettingsActions(
    val onBack: () -> Unit,
    val onChange: (NotificationChoice) -> Unit,
    val onRetry: () -> Unit,
    val onPhoneSettings: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    state: SettingsUiState,
    actions: NotificationSettingsActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_notifications_settings)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_notifications_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val inner = Modifier.fillMaxSize().padding(padding)
        when {
            state.loading && state.choices.isEmpty() -> {
                SkeletonScreen(inner) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(PLACEHOLDERS) { SkeletonBlock(Modifier.fillMaxWidth().padding(vertical = 4.dp)) }
                    }
                }
            }

            state.error != null && state.choices.isEmpty() -> {
                ErrorState(state.error, onRetry = actions.onRetry, modifier = inner)
            }

            else -> {
                ChoiceList(state.choices, actions, inner)
            }
        }
    }
}

@Composable
private fun ChoiceList(
    choices: List<NotificationChoice>,
    actions: NotificationSettingsActions,
    modifier: Modifier,
) {
    LazyColumn(modifier.readableWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                stringResource(R.string.feature_notifications_settings_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = BookStoreTheme.colors.textMuted,
                modifier = Modifier.padding(16.dp),
            )
        }
        items(choices, key = { it.category.id }) { choice ->
            ChoiceRow(choice, actions.onChange)
            HorizontalDivider()
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.feature_notifications_phone_settings)) },
                supportingContent = { Text(stringResource(R.string.feature_notifications_phone_settings_body)) },
                trailingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
                modifier = Modifier.toggleable(value = false, role = Role.Button, onValueChange = { actions.onPhoneSettings() }),
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    choice: NotificationChoice,
    onChange: (NotificationChoice) -> Unit,
) {
    val text = categoryText(choice)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text.first, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(text.second, style = MaterialTheme.typography.bodyMedium, color = BookStoreTheme.colors.textMuted)
        SwitchLine(
            label = stringResource(R.string.feature_notifications_in_app),
            checked = choice.inApp,
            onChange = { onChange(choice.copy(inApp = it)) },
        )
        if (choice.emailAvailable) {
            SwitchLine(
                label = stringResource(R.string.feature_notifications_by_email),
                checked = choice.email,
                onChange = { onChange(choice.copy(email = it)) },
            )
        }
    }
}

@Composable
private fun SwitchLine(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** The category's name and what it covers, in the app's language; the server's words for one it does not know. */
@Composable
private fun categoryText(choice: NotificationChoice): Pair<String, String> {
    val ids =
        when (choice.category) {
            NotificationCategory.Orders -> {
                R.string.feature_notifications_cat_orders to R.string.feature_notifications_cat_orders_body
            }

            NotificationCategory.Returns -> {
                R.string.feature_notifications_cat_returns to R.string.feature_notifications_cat_returns_body
            }

            NotificationCategory.Payouts -> {
                R.string.feature_notifications_cat_payouts to R.string.feature_notifications_cat_payouts_body
            }

            NotificationCategory.Reviews -> {
                R.string.feature_notifications_cat_reviews to R.string.feature_notifications_cat_reviews_body
            }

            NotificationCategory.Stock -> {
                R.string.feature_notifications_cat_stock to R.string.feature_notifications_cat_stock_body
            }

            NotificationCategory.Deals -> {
                R.string.feature_notifications_cat_deals to R.string.feature_notifications_cat_deals_body
            }

            NotificationCategory.Wanted -> {
                R.string.feature_notifications_cat_wanted to R.string.feature_notifications_cat_wanted_body
            }

            NotificationCategory.Community -> {
                R.string.feature_notifications_cat_community to
                    R.string.feature_notifications_cat_community_body
            }

            NotificationCategory.Announcements -> {
                R.string.feature_notifications_cat_announcements to R.string.feature_notifications_cat_announcements_body
            }

            NotificationCategory.Moderation -> {
                R.string.feature_notifications_cat_moderation to
                    R.string.feature_notifications_cat_moderation_body
            }

            NotificationCategory.Account -> {
                null
            }
        }
    return ids?.let { stringResource(it.first) to stringResource(it.second) } ?: (choice.label to choice.description)
}

private const val PLACEHOLDERS = 6
