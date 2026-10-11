package io.github.utshabasak.bookstorebd.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.designsystem.theme.brandGradient
import io.github.utshabasak.bookstorebd.core.model.OwnProfile
import io.github.utshabasak.bookstorebd.core.model.ProfileMode
import io.github.utshabasak.bookstorebd.core.model.SetupItem
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount

/** The banner, photo, name and e-mail at the top of a signed-in Account tab. */
@Composable
internal fun ProfileHeader(
    profile: OwnProfile?,
    username: String,
    email: String,
    onEdit: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(BANNER_HEIGHT)
                .background(brandGradient()),
        ) {
            profile?.buyerBannerUrl?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = BANNER_HEIGHT - AVATAR_SIZE / 2),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                Modifier
                    .size(AVATAR_SIZE)
                    .clip(CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val picture = profile?.pictureUrl
                if (picture != null) {
                    AsyncImage(
                        model = picture,
                        contentDescription = stringResource(R.string.feature_settings_profile_picture),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        username.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp, bottom = 4.dp)) {
                Text(
                    username,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp).offset(y = 4.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.feature_settings_edit), Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** The website's "finish setting up" card: progress, encouragement, and the steps left. Hidden when complete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SetupCard(
    profile: OwnProfile,
    onStep: (SetupItem) -> Unit,
) {
    val steps = profile.setupSteps(ProfileMode.Buyer)
    val done = steps.count { it.done }
    if (done == steps.size) return
    val percent = done * PERCENT / steps.size
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.feature_settings_setup_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text(
                    stringResource(R.string.feature_settings_setup_percent, formatCount(percent, currentLocale())),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            LinearProgressIndicator(progress = { done.toFloat() / steps.size }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
            val left = steps.size - done
            Text(
                when {
                    percent >= ALMOST -> pluralStringResourceCompat(R.plurals.feature_settings_setup_almost, left)
                    percent >= GOOD_START -> stringResource(R.string.feature_settings_setup_good)
                    else -> stringResource(R.string.feature_settings_setup_welcome)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                steps.forEach { step ->
                    AssistChip(
                        onClick = { onStep(step.item) },
                        enabled = !step.done,
                        label = { Text(stringResource(setupLabel(step.item))) },
                        leadingIcon = {
                            Icon(
                                if (step.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (step.done) BookStoreTheme.colors.success else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun pluralStringResourceCompat(
    id: Int,
    count: Int,
): String = androidx.compose.ui.res.pluralStringResource(id, count, formatCount(count, currentLocale()))

private fun setupLabel(item: SetupItem): Int =
    when (item) {
        SetupItem.Photo -> R.string.feature_settings_setup_photo
        SetupItem.Phone -> R.string.feature_settings_setup_phone
        SetupItem.Address -> R.string.feature_settings_setup_address
        SetupItem.DateOfBirth -> R.string.feature_settings_setup_birthday
        SetupItem.Banner -> R.string.feature_settings_setup_banner
        SetupItem.TwoStep -> R.string.feature_settings_setup_two_step
        SetupItem.Bkash -> R.string.feature_settings_setup_bkash
    }

/** Quick ways to the places people go most from their account. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Shortcuts(
    actions: AccountActions,
    inbox: InboxCounts,
) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        maxItemsInEachRow = 2,
    ) {
        Shortcut(Icons.AutoMirrored.Outlined.ListAlt, R.string.feature_settings_my_orders, actions.onOrders, Modifier.weight(1f))
        Shortcut(Icons.Outlined.FavoriteBorder, R.string.feature_settings_my_wishlist, actions.onWishlist, Modifier.weight(1f))
        Shortcut(
            Icons.AutoMirrored.Outlined.Chat,
            R.string.feature_settings_messages,
            actions.onMessages,
            Modifier.weight(1f),
            inbox.messages,
        )
        Shortcut(
            Icons.Outlined.Notifications,
            R.string.feature_settings_notifications,
            actions.onNotifications,
            Modifier.weight(1f),
            inbox.notifications,
        )
        Shortcut(Icons.AutoMirrored.Outlined.ManageSearch, R.string.feature_settings_wanted_board, actions.onWanted, Modifier.weight(1f))
        Shortcut(Icons.Outlined.GroupAdd, R.string.feature_settings_invite, actions.onInvite, Modifier.weight(1f))
    }
}

@Composable
private fun Shortcut(
    icon: ImageVector,
    label: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unread: Int = 0,
) {
    val locale = currentLocale()
    val name = stringResource(label)
    val spoken =
        if (unread >
            0
        ) {
            pluralStringResource(R.plurals.feature_settings_unread, unread, name, formatCount(unread, locale))
        } else {
            name
        }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.heightIn(min = 72.dp).clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 12.dp).weight(1f))
            if (unread > 0) Badge { Text(formatCount(unread, locale)) }
        }
    }
}

/** Two-step sign-in, on or off. Turning it off asks for the password. */
@Composable
internal fun TwoStepRow(
    enabled: Boolean,
    busy: Boolean,
    problem: String?,
    onChange: (Boolean, String?) -> Unit,
    onDismissProblem: () -> Unit,
) {
    var askPassword by rememberSaveable { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.feature_settings_two_step)) },
        supportingContent = {
            Text(stringResource(if (enabled) R.string.feature_settings_two_step_on else R.string.feature_settings_two_step_off))
        },
        leadingContent = { Icon(Icons.Outlined.Security, contentDescription = null) },
        trailingContent = {
            if (busy && !askPassword) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Switch(checked = enabled, onCheckedChange = null)
            }
        },
        modifier =
            Modifier.toggleable(value = enabled, enabled = !busy, role = Role.Switch) { turnOn ->
                if (turnOn) onChange(true, null) else askPassword = true
            },
    )
    if (askPassword || problem != null) {
        var password by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                askPassword = false
                onDismissProblem()
            },
            title = { Text(stringResource(R.string.feature_settings_two_step_off_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.feature_settings_two_step_off_body))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.feature_settings_password)) },
                        isError = problem != null,
                        supportingText = problem?.let { { Text(it) } },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onChange(false, password)
                    askPassword = false
                }, enabled = password.isNotEmpty() && !busy) {
                    Text(stringResource(R.string.feature_settings_two_step_turn_off))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    askPassword = false
                    onDismissProblem()
                }) { Text(stringResource(R.string.feature_settings_two_step_keep)) }
            },
        )
    }
}

private val BANNER_HEIGHT = 120.dp
private val AVATAR_SIZE = 80.dp
private const val PERCENT = 100
private const val ALMOST = 70
private const val GOOD_START = 30
