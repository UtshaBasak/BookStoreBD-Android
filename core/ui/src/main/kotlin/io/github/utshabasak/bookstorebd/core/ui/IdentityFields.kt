package io.github.utshabasak.bookstorebd.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.IdentityCheck
import io.github.utshabasak.bookstorebd.core.model.digitsOnly

/** Sending the e-mailed code, for an account with no password: where it stands. */
data class CodeState(
    val sending: Boolean = false,
    /** The shop's word that the code is on its way; null until it is sent. */
    val sent: String? = null,
)

/**
 * The re-check before a change that cannot be undone, or that weakens the
 * account, as the website asks it: the password, or - for an account made
 * with Google, which has none - a code e-mailed to the account's address.
 */
@Composable
fun IdentityFields(
    passwordSet: Boolean,
    check: IdentityCheck,
    onChange: (IdentityCheck) -> Unit,
    code: CodeState,
    onSendCode: () -> Unit,
    modifier: Modifier = Modifier,
    problem: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (passwordSet) {
            OutlinedTextField(
                value = check.password,
                onValueChange = { onChange(check.copy(password = it)) },
                label = { Text(stringResource(R.string.core_ui_identity_password)) },
                isError = problem != null,
                supportingText = problem?.let { { Text(it) } },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }
        Text(stringResource(R.string.core_ui_identity_google), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onSendCode, enabled = !code.sending, modifier = Modifier.heightIn(min = 48.dp)) {
            if (code.sending) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(if (code.sent != null) R.string.core_ui_identity_send_again else R.string.core_ui_identity_send))
            }
        }
        code.sent?.let { sent ->
            Text(
                sent,
                style = MaterialTheme.typography.bodySmall,
                color = BookStoreTheme.colors.success,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            OutlinedTextField(
                value = check.code,
                onValueChange = { typed -> onChange(check.copy(code = typed.digitsOnly().take(CODE_LENGTH))) },
                label = { Text(stringResource(R.string.core_ui_identity_code)) },
                isError = problem != null,
                supportingText = problem?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (code.sent == null && problem != null) {
            Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

private const val CODE_LENGTH = 6
