package io.github.utshabasak.bookstorebd

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.ThemePreference
import io.github.utshabasak.bookstorebd.core.ui.LocalPrivateImageLoader
import io.github.utshabasak.bookstorebd.core.ui.navigation.routeForSiteLink
import io.github.utshabasak.bookstorebd.core.ui.openInBrowser
import io.github.utshabasak.bookstorebd.di.PrivateImages
import io.github.utshabasak.bookstorebd.push.PushNotifier
import io.github.utshabasak.bookstorebd.ui.AppViewModel
import io.github.utshabasak.bookstorebd.ui.BookStoreApp
import javax.inject.Inject

/**
 * The only activity. AppCompat rather than ComponentActivity because it applies
 * the per-app language on Android 12 and below.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val appViewModel: AppViewModel by viewModels()

    @Inject @PrivateImages
    lateinit var privateImages: ImageLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The splash stays until the saved theme is read, so the first frame is in the right colours.
        splash.setKeepOnScreenCondition { appViewModel.theme.value == null }
        if (savedInstanceState == null) openFrom(intent)
        AppShortcut.publish(this)
        setContent {
            val theme by appViewModel.theme.collectAsStateWithLifecycle()
            val dark =
                when (theme) {
                    ThemePreference.Light -> false
                    ThemePreference.Dark -> true
                    ThemePreference.System, null -> isSystemInDarkTheme()
                }
            DisposableEffect(dark) {
                // Status and navigation bar icons that read against the app's theme, not the phone's.
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            BookStoreTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalPrivateImageLoader provides privateImages) {
                    BookStoreApp(appViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFrom(intent)
    }

    /** A tapped notification, a link to the website or a home-screen shortcut opens its screen. */
    private fun openFrom(intent: Intent?) {
        when (intent?.action) {
            PushNotifier.ACTION_OPEN -> {
                openNotification(intent)
            }

            Intent.ACTION_VIEW -> {
                intent.data?.let(::openLink)
            }

            AppShortcut.ACTION -> {
                val shortcut = AppShortcut.forId(intent.getStringExtra(AppShortcut.EXTRA_ID)) ?: return
                ShortcutManagerCompat.reportShortcutUsed(this, shortcut.id)
                appViewModel.open(shortcut.route)
            }
        }
    }

    /** A link to the website: its screen in the app, or the page in the browser when the app has none. */
    private fun openLink(uri: Uri) {
        val route = if (uri.host == BuildConfig.LINK_HOST) routeForSiteLink(sitePath(uri)) else null
        if (route != null) appViewModel.open(route) else openInBrowser(this, uri.toString())
    }

    private fun openNotification(intent: Intent) {
        val chat = intent.getStringExtra(PushNotifier.EXTRA_CHAT_EMAIL)
        if (chat != null) {
            appViewModel.openChat(chat, intent.getStringExtra(PushNotifier.EXTRA_CHAT_NAME))
        } else if (intent.getBooleanExtra(PushNotifier.EXTRA_MESSAGES, false)) {
            appViewModel.openMessages()
        } else {
            appViewModel.openNotification(
                intent.getStringExtra(PushNotifier.EXTRA_LINK),
                intent.getStringExtra(PushNotifier.EXTRA_NOTIFICATION_ID),
            )
        }
    }
}

/** A link's path, query and fragment, as the website's own links write them. */
internal fun sitePath(uri: Uri): String =
    buildString {
        append(uri.path.orEmpty().ifEmpty { "/" })
        uri.encodedQuery?.let { append('?').append(it) }
        uri.encodedFragment?.let { append('#').append(it) }
    }
