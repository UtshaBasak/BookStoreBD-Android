package io.github.utshabasak.bookstorebd.core.ui

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the shopping assistant is offered: true only once the server has
 * said it is switched on. While false, no screen shows a way to it, and the
 * app looks as it does without the assistant.
 */
val LocalAssistantOffered = compositionLocalOf { false }
