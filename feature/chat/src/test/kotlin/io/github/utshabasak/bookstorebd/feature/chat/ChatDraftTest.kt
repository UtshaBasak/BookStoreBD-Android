package io.github.utshabasak.bookstorebd.feature.chat

import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.image.ImagePreparer
import io.github.utshabasak.bookstorebd.core.data.inbox.ActiveConversation
import io.github.utshabasak.bookstorebd.core.data.inbox.ChatPage
import io.github.utshabasak.bookstorebd.core.data.inbox.ChatRepository
import io.github.utshabasak.bookstorebd.core.data.inbox.LiveEvent
import io.github.utshabasak.bookstorebd.core.data.inbox.LiveUpdates
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.SessionUser
import io.github.utshabasak.bookstorebd.core.testing.MainDispatcherRule
import io.github.utshabasak.bookstorebd.core.ui.navigation.ChatRoute
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatDraftTest {
    @get:Rule val main = MainDispatcherRule()

    private val me = SessionUser("u1", "nusrat", "nusrat@example.com", false)
    private val seller = "seller@example.com"
    private val repository =
        mockk<ChatRepository>(relaxed = true) {
            every { conversations } returns MutableStateFlow(null)
            coEvery { messages(seller, 1) } returns Outcome.Success(ChatPage(emptyList(), hasOlder = false))
        }

    private fun model(route: ChatRoute) =
        ChatViewModel(
            route,
            repository,
            mockk<ImagePreparer>(relaxed = true),
            ActiveConversation(),
            mockk<LiveUpdates> { every { updates } returns MutableSharedFlow<LiveEvent>() },
            mockk<UserSession> { coEvery { currentUser() } returns me },
        )

    @Test
    fun `a draft handed over fills the box and waits for the person to send it`() =
        runTest {
            val draft = "About \"Himu\". My questions so far:\n- Is it new?"

            val state = model(ChatRoute(seller, "boipoka", draft = draft)).uiState.value

            assertThat(state.draft).isEqualTo(draft)
            // Nothing is on its way and nothing was sent: the conversation is as the server has it.
            assertThat(state.bubbles).isEmpty()
        }

    @Test
    fun `a conversation opened as before starts with an empty box`() =
        runTest {
            assertThat(model(ChatRoute(seller, "boipoka")).uiState.value.draft).isEmpty()
        }
}
