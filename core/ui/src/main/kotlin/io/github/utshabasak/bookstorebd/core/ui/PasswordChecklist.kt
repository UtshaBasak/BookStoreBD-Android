package io.github.utshabasak.bookstorebd.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.PasswordCheck

/** The shop's password rules, each ticked as it is met, from the server's own check. */
@Composable
fun PasswordChecklist(check: PasswordCheck?) {
    if (check == null) {
        Text(
            stringResource(R.string.core_ui_password_hint),
            style = MaterialTheme.typography.bodySmall,
            color = BookStoreTheme.colors.textMuted,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PASSWORD_RULES.forEach { (id, label) ->
            val met = id !in check.problems
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (met) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (met) BookStoreTheme.colors.success else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(label) + if (met) "" else " — " + stringResource(R.string.core_ui_password_rule_not_met),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                )
            }
        }
        // A password in a known breach passes every rule above; the server says so in its message.
        if (check.problems.isEmpty() && !check.ok && check.message.isNotBlank()) {
            Text(check.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

private val PASSWORD_RULES =
    listOf(
        "length" to R.string.core_ui_password_rule_length,
        "lower" to R.string.core_ui_password_rule_lower,
        "upper" to R.string.core_ui_password_rule_upper,
        "number" to R.string.core_ui_password_rule_number,
        "symbol" to R.string.core_ui_password_rule_symbol,
        "personal" to R.string.core_ui_password_rule_personal,
        "common" to R.string.core_ui_password_rule_common,
        "pattern" to R.string.core_ui_password_rule_pattern,
    )
