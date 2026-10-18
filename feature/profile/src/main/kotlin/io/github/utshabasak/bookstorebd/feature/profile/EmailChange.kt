package io.github.utshabasak.bookstorebd.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.digitsOnly
import io.github.utshabasak.bookstorebd.core.ui.errorMessage

/**
 * Moving the account to another e-mail address, as the website does it: the
 * new address and the password, then the code sent to the new address.
 * Nothing moves until the code is entered.
 */
data class EmailChangeState(
    val open: Boolean = false,
    val newEmail: String = "",
    val password: String = "",
    val code: String = "",
    /** An address already waiting for its code. */
    val waitingFor: String? = null,
    val busy: Boolean = false,
    val problem: AppError? = null,
) {
    val canStart: Boolean get() = !busy && newEmail.isNotBlank() && password.isNotEmpty()
    val canConfirm: Boolean get() = !busy && CODE.matches(code.trim())

    private companion object {
        val CODE = Regex("^\\d{6}$")
    }
}

/** What the e-mail change can do. */
class EmailChangeActions(
    val onOpen: () -> Unit,
    val onClose: () -> Unit,
    val onEdit: ((EmailChangeState) -> EmailChangeState) -> Unit,
    val onStart: () -> Unit,
    val onConfirm: () -> Unit,
    val onOtherAddress: () -> Unit,
)

@Composable
internal fun EmailChange(
    currentEmail: String,
    passwordSet: Boolean,
    state: EmailChangeState,
    actions: EmailChangeActions,
) {
    if (!state.open) {
        Column {
            state.waitingFor?.let {
                Text(
                    stringResource(R.string.feature_profile_email_waiting, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = actions.onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    stringResource(
                        if (state.waitingFor != null) R.string.feature_profile_enter_code else R.string.feature_profile_change_email,
                    ),
                )
            }
        }
        return
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                // An account made with Google has no password: it chooses one first.
                !passwordSet -> {
                    Text(stringResource(R.string.feature_profile_email_google), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = actions.onClose) { Text(stringResource(R.string.feature_profile_cancel)) }
                }

                state.waitingFor != null -> {
                    Text(
                        stringResource(R.string.feature_profile_email_code_sent, state.waitingFor, currentEmail),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = state.code,
                        onValueChange = { typed -> actions.onEdit { it.copy(code = typed.digitsOnly().take(CODE_LENGTH)) } },
                        label = { Text(stringResource(R.string.feature_profile_code)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = actions.onConfirm, enabled = state.canConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.feature_profile_confirm_email))
                    }
                    TextButton(onClick = actions.onOtherAddress) { Text(stringResource(R.string.feature_profile_other_address)) }
                }

                else -> {
                    OutlinedTextField(
                        value = state.newEmail,
                        onValueChange = { typed -> actions.onEdit { it.copy(newEmail = typed.trim()) } },
                        label = { Text(stringResource(R.string.feature_profile_new_email)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { typed -> actions.onEdit { it.copy(password = typed) } },
                        label = { Text(stringResource(R.string.feature_profile_your_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = actions.onStart, enabled = state.canStart, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.feature_profile_send_email_code))
                    }
                    TextButton(onClick = actions.onClose) { Text(stringResource(R.string.feature_profile_cancel)) }
                }
            }
            state.problem?.let {
                Text(
                    errorMessage(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
        }
    }
}

private const val CODE_LENGTH = 6
