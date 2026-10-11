package io.github.utshabasak.bookstorebd.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.model.AppError

/** A spinner that says what it is waiting for, to TalkBack and on screen. */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.core_ui_loading),
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp)
                .semantics(mergeDescendants = true) { contentDescription = message },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/** What went wrong, in plain words, with a way to try again. Never a blank screen. */
@Composable
fun ErrorState(
    error: AppError,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    MessageState(
        icon = if (error == AppError.Offline) Icons.Outlined.CloudOff else Icons.Outlined.ErrorOutline,
        title = errorTitle(error),
        body = errorMessage(error),
        modifier = modifier,
        action = onRetry?.let { retry -> { Button(onClick = retry) { Text(stringResource(R.string.core_ui_try_again)) } } },
    )
}

/** An empty list, explained, with the obvious next step. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    MessageState(
        icon = icon,
        title = title,
        body = body,
        modifier = modifier,
        action =
            if (actionLabel != null && onAction != null) {
                { OutlinedButton(onClick = onAction) { Text(actionLabel) } }
            } else {
                null
            },
    )
}

@Composable
private fun MessageState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier,
    action: (@Composable () -> Unit)?,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .padding(top = 16.dp)
                    .semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (action != null) {
            Box(Modifier.padding(top = 24.dp)) { action() }
        }
    }
}

/** A strip under the top bar while the phone is offline: what is shown was saved earlier. */
@Composable
fun OfflineBanner(
    offline: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = offline, modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.inverseSurface)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface)
            Text(
                text = stringResource(R.string.core_ui_offline_banner),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}

@Composable
fun errorTitle(error: AppError): String =
    stringResource(
        when (error) {
            AppError.Offline -> R.string.core_ui_error_offline_title
            AppError.Timeout -> R.string.core_ui_error_timeout_title
            AppError.SignInRequired -> R.string.core_ui_error_signin_title
            else -> R.string.core_ui_error_title
        },
    )

/**
 * The sentence to show for an error. The server's own wording is used where
 * it is meant for people - a validation message, a refusal - and the app's
 * otherwise, in the app's language.
 */
@Composable
fun errorMessage(error: AppError): String =
    when (error) {
        AppError.Offline -> stringResource(R.string.core_ui_error_offline)
        AppError.Timeout -> stringResource(R.string.core_ui_error_timeout)
        AppError.SignInRequired -> stringResource(R.string.core_ui_error_signin)
        AppError.UpdateRequired -> stringResource(R.string.core_ui_error_update)
        AppError.CaptchaRequired -> stringResource(R.string.core_ui_error_captcha)
        is AppError.RateLimited -> error.message ?: stringResource(R.string.core_ui_error_rate_limited)
        is AppError.Rejected -> error.fieldErrors.values.firstOrNull() ?: error.message ?: stringResource(R.string.core_ui_error_generic)
        is AppError.Server -> stringResource(R.string.core_ui_error_server)
        is AppError.Unexpected -> stringResource(R.string.core_ui_error_generic)
    }

/** Shows [error] once in [snackbar], in the app's words, then reports it shown. */
@Composable
fun ErrorSnackbarEffect(
    error: AppError?,
    snackbar: SnackbarHostState,
    onShown: () -> Unit,
) {
    val text = error?.let { errorMessage(it) }
    LaunchedEffect(text) {
        if (text != null) {
            snackbar.showSnackbar(text)
            onShown()
        }
    }
}
