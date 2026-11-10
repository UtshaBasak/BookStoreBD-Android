package io.github.utshabasak.bookstorebd.feature.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.HomeSections
import io.github.utshabasak.bookstorebd.core.ui.LocalAssistantOffered
import io.github.utshabasak.bookstorebd.core.ui.navigation.AssistantRoute
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AssistantOfferTest {
    @get:Rule val compose = createComposeRule()

    private var opened: NavKey? = null

    private fun show(
        offered: Boolean,
        inbox: InboxBadges = InboxBadges(),
    ) = compose.setContent {
        CompositionLocalProvider(LocalAssistantOffered provides offered) {
            BookStoreTheme {
                HomeScreen(
                    state = HomeUiState(sections = HomeSections()),
                    onRefresh = {},
                    onSearch = {},
                    onOpenBook = {},
                    onToggleWishlist = {},
                    onOpenCatalogue = {},
                    inbox = inbox,
                    onInbox = { opened = it },
                )
            }
        }
    }

    @Test
    fun `with the assistant switched off, Home has no way to it`() {
        show(offered = false, inbox = InboxBadges(signedIn = true))

        compose.onNodeWithContentDescription(ASK).assertDoesNotExist()
        compose.onNodeWithContentDescription(MESSAGES).assertExists()
    }

    @Test
    fun `the assistant is offered to a visitor too, and opens from the top bar`() {
        show(offered = true)

        compose.onNodeWithContentDescription(ASK).performClick()

        assertThat(opened).isEqualTo(AssistantRoute())
    }

    @Test
    fun `signed in, the assistant sits before messages and the bell`() {
        show(offered = true, inbox = InboxBadges(signedIn = true))

        val ask = compose.onNodeWithContentDescription(ASK).fetchSemanticsNode().boundsInRoot
        val messages = compose.onNodeWithContentDescription(MESSAGES).fetchSemanticsNode().boundsInRoot
        assertThat(ask.right).isAtMost(messages.left)
    }
}

private const val ASK = "Ask the shopping assistant"
private const val MESSAGES = "Messages, 0 unread"
