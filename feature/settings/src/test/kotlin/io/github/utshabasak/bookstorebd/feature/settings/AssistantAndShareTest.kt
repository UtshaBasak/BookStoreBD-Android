package io.github.utshabasak.bookstorebd.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.SiteLinks
import io.github.utshabasak.bookstorebd.core.ui.LocalAssistantOffered
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp")
class AssistantAndShareTest {
    @get:Rule val compose = createComposeRule()

    private val links =
        SiteLinks("https://shop.example", "https://shop.example/privacy", "t", "r", "h", contact = "https://shop.example/contact")
    private var assistant = 0
    private var shared: String? = null

    private fun show(
        offered: Boolean,
        shareUrl: String = "https://example.com/get-the-app",
    ) = compose.setContent {
        CompositionLocalProvider(LocalAssistantOffered provides offered) {
            BookStoreTheme {
                AccountScreen(
                    state = AccountUiState(sessionKnown = true, links = links),
                    update = UpdateUiState(currentVersion = "1.1.0", shareUrl = shareUrl),
                    actions = AccountActions({}, {}, {}, {}, {}, {}, {}, {}, {}, onAssistant = { assistant++ }, onShare = { shared = it }),
                )
            }
        }
    }

    @Test
    fun `the Account tab shares the app's own link`() {
        show(offered = false)

        compose.onNodeWithText("Share the app").performScrollTo().performClick()

        assertThat(shared).isEqualTo("https://example.com/get-the-app")
    }

    @Test
    fun `a build that names no link offers no sharing`() {
        show(offered = false, shareUrl = "")

        compose.onNodeWithText("Share the app").assertDoesNotExist()
    }

    @Test
    fun `the assistant is beside the shop's contact page while it is switched on`() {
        show(offered = true)

        compose.onNodeWithText("Ask the shopping assistant").performScrollTo().performClick()

        assertThat(assistant).isEqualTo(1)
    }

    @Test
    fun `with the assistant switched off, the Account tab has no way to it`() {
        show(offered = false)

        compose.onNodeWithText("Ask the shopping assistant").assertDoesNotExist()
    }
}
