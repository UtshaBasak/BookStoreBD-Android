package io.github.utshabasak.bookstorebd.core.scanner

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScannerScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a refused camera is explained, and can be asked for again or skipped`() {
        var askedAgain = false
        var dismissed = false
        compose.setContent {
            BookStoreTheme {
                ScannerScreen(
                    access = CameraAccess.Refused,
                    onScanned = {},
                    onDismiss = { dismissed = true },
                    onAskAgain = { askedAgain = true },
                    onOpenSettings = {},
                )
            }
        }

        compose.onNodeWithText("Allow the camera to scan").assertIsDisplayed()
        compose.onNodeWithText("Allow the camera").performClick()
        compose.onNodeWithText("Type it instead").performClick()
        assertThat(askedAgain).isTrue()
        assertThat(dismissed).isTrue()
    }

    @Test
    fun `a camera refused for good points to the phone's settings`() {
        var settings = false
        compose.setContent {
            BookStoreTheme {
                ScannerScreen(
                    access = CameraAccess.Blocked,
                    onScanned = {},
                    onDismiss = {},
                    onAskAgain = {},
                    onOpenSettings = { settings = true },
                )
            }
        }

        compose.onNodeWithText("Open settings").performClick()
        assertThat(settings).isTrue()
    }
}
