package io.github.utshabasak.bookstorebd.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * A physical keyboard's Enter key (a tablet's keyboard cover, a Chromebook)
 * does what the on-screen keyboard's action key does: [onEnter] runs when it
 * is released. The key is consumed, so a single-line field never receives it.
 */
fun Modifier.onHardwareEnter(onEnter: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
        if (isEnter && event.type == KeyEventType.KeyUp) onEnter()
        isEnter
    }
