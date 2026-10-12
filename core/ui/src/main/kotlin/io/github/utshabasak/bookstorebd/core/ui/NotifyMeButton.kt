package io.github.utshabasak.bookstorebd.core.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * "Notify me when it is back", for a sold-out book: the seller hears somebody
 * is waiting, and the person hears when copies are added. Pressed again, it
 * withdraws the request, as on the website.
 */
@Composable
fun NotifyMeButton(
    requested: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val state = stringResource(if (requested) R.string.core_ui_notify_requested_state else R.string.core_ui_notify_not_requested_state)
    OutlinedButton(
        onClick = onToggle,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).semantics { stateDescription = state },
    ) {
        Icon(
            if (requested) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsNone,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            stringResource(if (requested) R.string.core_ui_notify_requested else R.string.core_ui_notify_me),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
