package io.github.utshabasak.bookstorebd.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import io.github.utshabasak.bookstorebd.core.model.Gender
import io.github.utshabasak.bookstorebd.core.model.OwnProfile
import io.github.utshabasak.bookstorebd.core.model.ProfileMode
import io.github.utshabasak.bookstorebd.core.model.SetupItem
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatDate

/** The banner, photo, name and e-mail at the top of a signed-in Account tab. */
@Composable
internal fun ProfileHeader(
    profile: OwnProfile?,
    username: String,
    email: String,
    mode: ProfileMode,
    onEdit: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(BANNER_HEIGHT)
                .background(brandGradient()),
        ) {
            (if (mode == ProfileMode.Seller) profile?.sellerBannerUrl else profile?.buyerBannerUrl)?.let {
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

/** Buyer profile or Seller profile: which side of the account the tab shows, as on the website. */
@Composable
internal fun ModeSwitch(
    mode: ProfileMode,
    onMode: (ProfileMode) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        ProfileMode.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onMode(option) },
                shape = SegmentedButtonDefaults.itemShape(index, ProfileMode.entries.size),
            ) {
                Text(
                    stringResource(
                        if (option ==
                            ProfileMode.Seller
                        ) {
                            R.string.feature_settings_seller_profile
                        } else {
                            R.string.feature_settings_buyer_profile
                        },
                    ),
                )
            }
        }
    }
}

/** The details the person has given: birthday, gender, address and phone, each only when filled in. */
@Composable
internal fun ProfileDetails(profile: OwnProfile) {
    val locale = currentLocale()
    val details =
        listOfNotNull(
            profile.dateOfBirth.takeIf {
                it.isNotBlank()
            }?.let { Triple(Icons.Outlined.Cake, R.string.feature_settings_birthday, formatDate(it, locale)) },
            profile.gender?.let { gender ->
                Triple(
                    Icons.Outlined.Person,
                    R.string.feature_settings_gender,
                    stringResource(
                        if (gender ==
                            Gender.Female
                        ) {
                            R.string.feature_settings_gender_female
                        } else {
                            R.string.feature_settings_gender_male
                        },
                    ),
                )
            },
            profile.address.takeIf { it.isNotBlank() }?.let { Triple(Icons.Outlined.Home, R.string.feature_settings_address, it) },
            profile.phone.takeIf { it.isNotBlank() }?.let { Triple(Icons.Outlined.Phone, R.string.feature_settings_phone, it) },
        )
    if (details.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        details.forEach { (icon, label, value) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Where sales are paid, or, without a bKash number, why listing will not work yet. */
@Composable
internal fun PayoutNote(
    profile: OwnProfile,
    onAdd: () -> Unit,
) {
    val ok = profile.canSell
    Surface(
        color = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber, contentDescription = null)
            Text(
                if (ok) {
                    stringResource(
                        R.string.feature_settings_paid_to_bkash,
                        profile.bkashMerchant,
                    )
                } else {
                    stringResource(R.string.feature_settings_bkash_needed)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            if (!ok) TextButton(onClick = onAdd) { Text(stringResource(R.string.feature_settings_add_it_now)) }
        }
    }
}

/** The shop's e-mail address, opening the phone's e-mail app, and where the shop is. */
@Composable
internal fun GetInTouch(
    email: String,
    location: String,
    onEmail: (String) -> Unit,
) {
    ListItem(
        headlineContent = { Text(email) },
        supportingContent = if (location.isNotBlank()) ({ Text(location) }) else null,
        leadingContent = { Icon(Icons.Outlined.Email, contentDescription = null) },
        modifier =
            Modifier.clickable(onClickLabel = stringResource(R.string.feature_settings_email_us), role = Role.Button) {
                onEmail(email)
            },
    )
}

/** The website's "finish setting up" card: progress, encouragement, and the steps left. Hidden when complete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SetupCard(
    profile: OwnProfile,
    mode: ProfileMode,
    onStep: (SetupItem) -> Unit,
) {
    val steps = profile.setupSteps(mode)
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
            // The count sits on the icon, leaving the name the whole width, so a long one is not broken mid-word.
            BadgedBox(badge = { if (unread > 0) Badge { Text(formatCount(unread, locale)) } }) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 12.dp).weight(1f))
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
