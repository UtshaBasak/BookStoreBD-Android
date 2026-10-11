package io.github.utshabasak.bookstorebd

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.ThemePreference
import io.github.utshabasak.bookstorebd.ui.AppViewModel
import io.github.utshabasak.bookstorebd.ui.BookStoreApp

/**
 * The only activity. AppCompat rather than ComponentActivity because it applies
 * the per-app language on Android 12 and below.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The splash stays until the saved theme is read, so the first frame is in the right colours.
        splash.setKeepOnScreenCondition { appViewModel.theme.value == null }
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
                BookStoreApp(appViewModel)
            }
        }
    }
}
