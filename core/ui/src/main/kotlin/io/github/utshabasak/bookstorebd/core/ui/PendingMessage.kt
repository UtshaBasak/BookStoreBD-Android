package io.github.utshabasak.bookstorebd.core.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * A short confirmation from a form that has just closed ("Return requested"),
 * shown by the screen the person comes back to, so it is seen where they are
 * rather than lost with the form.
 */
object PendingMessage {
    private var text: String? = null

    fun post(message: String) {
        text = message
    }

    fun consume(): String? = text.also { text = null }
}

/** Shows a [PendingMessage] in [snackbar] when this screen appears. */
@Composable
fun PendingMessageEffect(snackbar: SnackbarHostState) {
    LaunchedEffect(Unit) { PendingMessage.consume()?.let { snackbar.showSnackbar(it) } }
}
