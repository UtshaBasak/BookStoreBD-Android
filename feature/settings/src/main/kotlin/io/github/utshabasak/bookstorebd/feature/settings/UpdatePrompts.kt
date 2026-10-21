package io.github.utshabasak.bookstorebd.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.model.AppRelease
import io.github.utshabasak.bookstorebd.core.ui.errorMessage

/**
 * Shown instead of the app until it is updated: when a newer release is out
 * (the GitHub build), or when the server no longer serves this build. The
 * download starts by itself; the button installs it, or retries. The website
 * stays a way in.
 */
@Composable
fun UpdateRequiredScreen(
    release: AppRelease?,
    status: UpdateStatus,
    canInstall: Boolean,
    onInstall: (AppRelease) -> Unit,
    onOpenReleases: () -> Unit,
    onOpenWebsite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            Icons.Outlined.SystemUpdate,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.feature_settings_required_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            if (release != null) {
                stringResource(R.string.feature_settings_required_new, release.versionName)
            } else {
                stringResource(R.string.feature_settings_required_body)
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (release != null && release.notes.isNotBlank()) {
            Text(
                release.notes.take(NOTES_PREVIEW),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        UpdateProgress(status)
        if (status is UpdateStatus.Ready) {
            Text(
                stringResource(R.string.feature_settings_update_ready),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        if (release != null && canInstall) {
            Button(
                onClick = { onInstall(release) },
                enabled = status !is UpdateStatus.Downloading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.feature_settings_update_install))
            }
        } else {
            Button(onClick = onOpenReleases, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.feature_settings_update_open))
            }
        }
        OutlinedButton(onClick = onOpenWebsite, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.feature_settings_use_website))
        }
    }
}

@Composable
private fun UpdateProgress(status: UpdateStatus) {
    when (status) {
        is UpdateStatus.Downloading -> {
            Text(
                stringResource(R.string.feature_settings_update_downloading, (status.progress * PERCENT).toInt()),
                style = MaterialTheme.typography.bodySmall,
            )
            LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
        }

        is UpdateStatus.Failed -> {
            Text(errorMessage(status.error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        else -> {}
    }
}

private const val NOTES_PREVIEW = 1_200
private const val PERCENT = 100
