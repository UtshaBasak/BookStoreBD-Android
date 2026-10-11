package io.github.utshabasak.bookstorebd.feature.chat

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.image.ImagePreparer
import io.github.utshabasak.bookstorebd.core.data.inbox.ActiveConversation
import io.github.utshabasak.bookstorebd.core.data.inbox.ChatPage
import io.github.utshabasak.bookstorebd.core.data.inbox.ChatRepository
import io.github.utshabasak.bookstorebd.core.data.inbox.LiveEvent
import io.github.utshabasak.bookstorebd.core.data.inbox.LiveUpdates
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.ChatMessage
import io.github.utshabasak.bookstorebd.core.model.Conversation
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.SessionUser
import io.github.utshabasak.bookstorebd.core.testing.MainDispatcherRule
import io.github.utshabasak.bookstorebd.core.ui.navigation.ChatRoute
import io.mockk.coEvery
import io.mockk.coVerify
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
class ChatTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val compose = createComposeRule()

    private val me = SessionUser("u1", "nusrat", "nusrat@example.com", false)
    private val seller = "seller@example.com"

    private fun message(
        id: String,
        from: String,
        text: String,
    ) = ChatMessage(id, from, if (from == seller) me.email else seller, text, null, "2026-10-11T10:00:00Z", read = true)

    private val live = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 8)

    private fun chatModel(repository: ChatRepository) =
        ChatViewModel(
            ChatRoute(seller, "boipoka"),
            repository,
            mockk<ImagePreparer>(relaxed = true),
            ActiveConversation(),
            mockk<LiveUpdates> { every { updates } returns live },
            mockk<UserSession> { coEvery { currentUser() } returns me },
        )

    private fun repository() =
        mockk<ChatRepository>(relaxed = true) {
            every { conversations } returns MutableStateFlow(null)
            coEvery { messages(seller, 1) } returns Outcome.Success(ChatPage(listOf(message("m1", seller, "Yes, it is")), hasOlder = false))
        }

    @Test
    fun `a message shows at once, and stays on screen to try again when it does not go`() =
        runTest {
            val repository = repository()
            coEvery { repository.send(seller, "Is it signed?", null) } returns Outcome.Failure(AppError.Offline)
            val vm = chatModel(repository)

            vm.setDraft("Is it signed?")
            vm.sendText()

            val last = vm.uiState.value.bubbles.last()
            assertThat(last.message.text).isEqualTo("Is it signed?")
            assertThat(last.failed).isTrue()
            assertThat(vm.uiState.value.draft).isEmpty()
            coVerify { repository.markRead(seller) }
        }

    @Test
    fun `a message from this person arrives live, and one from someone else does not join the thread`() =
        runTest {
            val vm = chatModel(repository())

            live.emit(LiveEvent.Message(message("m2", seller, "Still here")))
            live.emit(LiveEvent.Message(message("m3", "someone@example.com", "Hello")))

            assertThat(vm.uiState.value.bubbles.map { it.message.text }).containsExactly("Yes, it is", "Still here").inOrder()
        }

    @Test
    fun `the thread shows each side, and a message that did not go can be tried again`() {
        var retried: Bubble? = null
        val failed = Bubble(message("local-0", me.email, "Is it signed?"), mine = true, failed = true)
        compose.setContent {
            BookStoreTheme {
                ChatScreen(
                    name = "boipoka",
                    state =
                        ChatUiState(
                            me = me.email,
                            bubbles = listOf(Bubble(message("m1", seller, "Yes, it is"), mine = false), failed),
                            loading = false,
                        ),
                    actions = ChatActions({}, {}, {}, {}, {}, {}, { retried = it }, {}, {}),
                )
            }
        }

        compose.onNodeWithText("Yes, it is").assertExists()
        compose.onNodeWithContentDescription("Not sent. Tap to try again.").performClick()
        assertThat(retried).isEqualTo(failed)
    }

    @Test
    fun `conversations show who, the last message, and how many are unread`() {
        var opened: Conversation? = null
        val conversation = Conversation(seller, "boipoka", null, "Yes, it is", "2026-10-11T10:00:00Z", unread = 2)
        compose.setContent {
            BookStoreTheme {
                MessagesScreen(
                    state = MessagesUiState(signedIn = true, conversations = listOf(conversation)),
                    actions = MessagesActions({}, {}, { opened = it }, {}),
                )
            }
        }

        compose.onNodeWithContentDescription("2 unread messages").assertExists()
        compose.onNodeWithText("boipoka").performClick()
        assertThat(opened).isEqualTo(conversation)
    }
}
