package io.github.utshabasak.bookstorebd.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import coil3.ImageLoader

/**
 * The image loader for pictures only the signed-in person may see, such as a
 * photo in a chat: it sends the session with each request, as the API does.
 * Provided by the app; null in previews and tests, where the default loader
 * is used.
 */
val LocalPrivateImageLoader = staticCompositionLocalOf<ImageLoader?> { null }
