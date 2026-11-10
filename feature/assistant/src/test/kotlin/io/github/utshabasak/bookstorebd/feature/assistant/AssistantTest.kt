package io.github.utshabasak.bookstorebd.feature.assistant

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.repository.AssistantRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CommunityRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.AssistantHandover
import io.github.utshabasak.bookstorebd.core.model.AssistantLink
import io.github.utshabasak.bookstorebd.core.model.AssistantReply
import io.github.utshabasak.bookstorebd.core.model.AssistantWantedOffer
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.ChatTurn
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.SessionUser
import io.github.utshabasak.bookstorebd.core.model.WantedDraft
import io.github.utshabasak.bookstorebd.core.model.WantedResult
import io.github.utshabasak.bookstorebd.core.testing.MainDispatcherRule
import io.github.utshabasak.bookstorebd.core.ui.navigation.AssistantRoute
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AssistantTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val compose = createComposeRule()

    private val me = SessionUser("u1", "nusrat", "nusrat@example.com", false)
    private val user = MutableStateFlow<SessionUser?>(null)
    private val assistant = mockk<AssistantRepository>(relaxed = true)
    private val community = mockk<CommunityRepository>(relaxed = true)
    private val configs =
        mockk<ConfigRepository>(relaxed = true) {
            every { config } returns flowOf(null)
            coEvery { current() } returns null
        }

    private fun model(prefill: String = "") =
        AssistantViewModel(AssistantRoute(prefill), assistant, community, configs, mockk<UserSession> { every { user } returns this@AssistantTest.user })

    private fun reply(
        text: String = "Delivery is ৳70 inside Dhaka.",
        id: String = "r1",
    ) = AssistantReply(requestId = id, text = text)

    private val noActions =
        AssistantActions({}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, { _, _ -> }, {})

    private fun show(
        state: AssistantUiState,
        actions: AssistantActions = noActions,
        fontScale: Float = 1f,
    ) = compose.setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            BookStoreTheme { AssistantScreen(state, actions) }
        }
    }

    private fun answered(reply: AssistantReply) =
        AssistantUiState(
            messages =
                listOf(
                    AssistantMessage(0, fromUser = true, text = "Do you have Himu?"),
                    AssistantMessage(1, fromUser = false, text = reply.text, reply = reply),
                ),
        )

    // ---------------------------------------------------------------- the conversation

    @Test
    fun `a question shows at once with Send held back, then its answer`() =
        runTest {
            val waiting = CompletableDeferred<Outcome<AssistantReply>>()
            coEvery { assistant.ask("delivery charge koto?", emptyList()) } coAnswers { waiting.await() }
            val model = model()

            model.setInput("delivery charge koto?")
            model.send()

            assertThat(model.uiState.value.pending).isTrue()
            assertThat(model.uiState.value.canSend).isFalse()
            assertThat(model.uiState.value.input).isEmpty()
            assertThat(model.uiState.value.messages.single().text).isEqualTo("delivery charge koto?")

            waiting.complete(Outcome.Success(reply()))

            val state = model.uiState.value
            assertThat(state.pending).isFalse()
            assertThat(state.messages.map { it.fromUser }).containsExactly(true, false).inOrder()
            assertThat(state.messages.last().text).isEqualTo("Delivery is ৳70 inside Dhaka.")
        }

    @Test
    fun `a second question waits for the first, so only one is ever on its way`() =
        runTest {
            val waiting = CompletableDeferred<Outcome<AssistantReply>>()
            coEvery { assistant.ask(any(), any()) } coAnswers { waiting.await() }
            val model = model()

            model.ask("first")
            model.ask("second")
            model.setInput("third")
            model.send()

            coVerify(exactly = 1) { assistant.ask(any(), any()) }
            assertThat(model.uiState.value.messages.map { it.text }).containsExactly("first")
        }

    @Test
    fun `earlier turns go back with the next question`() =
        runTest {
            coEvery { assistant.ask(any(), any()) } returns Outcome.Success(reply("An answer"))
            val model = model()

            model.ask("first")
            model.ask("second")

            coVerify { assistant.ask("second", listOf(ChatTurn(true, "first"), ChatTurn(false, "An answer"))) }
        }

    @Test
    fun `an answer given without the model, or to a question that is not about the shop, is an ordinary answer`() =
        runTest {
            coEvery { assistant.ask("busy?", any()) } returns Outcome.Success(reply("I am busy, but here is the help page.").copy(fallback = true))
            coEvery { assistant.ask("write me a poem", any()) } returns Outcome.Success(reply("I only help with the shop.", "r2").copy(outOfScope = true))
            val model = model()

            model.ask("busy?")
            model.ask("write me a poem")

            val state = model.uiState.value
            assertThat(state.error).isNull()
            assertThat(state.messages.filterNot { it.fromUser }.map { it.text })
                .containsExactly("I am busy, but here is the help page.", "I only help with the shop.")
                .inOrder()
        }

    @Test
    fun `a question over the limit is kept in the box with the server's words, and is not sent again`() =
        runTest {
            coEvery { assistant.ask(any(), any()) } returns Outcome.Failure(AppError.RateLimited("That is all for today."))
            val model = model()

            model.setInput("one more question")
            model.send()

            val state = model.uiState.value
            assertThat(state.error).isEqualTo(AppError.RateLimited("That is all for today."))
            assertThat(state.input).isEqualTo("one more question")
            assertThat(state.messages).isEmpty()
            assertThat(state.pending).isFalse()
            coVerify(exactly = 1) { assistant.ask(any(), any()) }
        }

    @Test
    fun `without a connection the question stays, and only the person's own tap sends it again`() =
        runTest {
            coEvery { assistant.ask(any(), any()) } returns Outcome.Failure(AppError.Offline) andThen Outcome.Success(reply())
            val model = model()

            model.ask("where is my order?")

            assertThat(model.uiState.value.error).isEqualTo(AppError.Offline)
            assertThat(model.uiState.value.input).isEqualTo("where is my order?")
            coVerify(exactly = 1) { assistant.ask(any(), any()) }

            model.send()

            assertThat(model.uiState.value.error).isNull()
            assertThat(model.uiState.value.messages).hasSize(2)
            coVerify(exactly = 2) { assistant.ask("where is my order?", emptyList()) }
        }

    @Test
    fun `a switched-off assistant takes no more questions`() =
        runTest {
            val off = AppError.Rejected(503, "The assistant is resting today.", AssistantRepository.CODE_OFF)
            coEvery { assistant.ask(any(), any()) } returns Outcome.Failure(off)
            val model = model()

            model.ask("hello")
            model.send()

            assertThat(model.uiState.value.available).isFalse()
            assertThat(model.uiState.value.canSend).isFalse()
            coVerify(exactly = 1) { assistant.ask(any(), any()) }
        }

    @Test
    fun `a thumbs up or down is sent once for an answer`() =
        runTest {
            coEvery { assistant.ask(any(), any()) } returns Outcome.Success(reply())
            val model = model()
            model.ask("delivery?")
            val answer = model.uiState.value.messages.last()

            model.feedback(answer.id, helpful = true)
            model.feedback(answer.id, helpful = false)

            assertThat(model.uiState.value.messages.last().feedbackSent).isTrue()
            coVerify(exactly = 1) { assistant.feedback("r1", true, null) }
            coVerify(exactly = 0) { assistant.feedback("r1", false, any()) }
        }

    @Test
    fun `a book is asked for on the Wanted board only when the button is pressed, and once`() =
        runTest {
            val offer = reply("We do not have it.").copy(wanted = AssistantWantedOffer("The Great Gatsby", ""))
            coEvery { assistant.ask(any(), any()) } returns Outcome.Success(offer)
            coEvery { community.askForBook(WantedDraft("The Great Gatsby")) } returns Outcome.Success(WantedResult.Created("Your request is on the board."))
            val model = model()

            model.ask("The Great Gatsby?")
            coVerify(exactly = 0) { community.askForBook(any()) }

            val answer = model.uiState.value.messages.last()
            model.askOnWantedBoard(answer.id)
            model.askOnWantedBoard(answer.id)

            assertThat(model.uiState.value.messages.last().wantedMessage).isEqualTo("Your request is on the board.")
            coVerify(exactly = 1) { community.askForBook(any()) }
        }

    @Test
    fun `Start over clears the conversation`() =
        runTest {
            coEvery { assistant.ask(any(), any()) } returns Outcome.Success(reply())
            val model = model()
            model.ask("delivery?")

            model.startOver()
            model.ask("returns?")

            assertThat(model.uiState.value.messages.map { it.text }.first()).isEqualTo("returns?")
            coVerify { assistant.ask("returns?", emptyList()) }
        }

    @Test
    fun `a search with no results arrives as a question ready to send, and signing in is noticed`() =
        runTest {
            val model = model(prefill = "padma nadir majhi")

            assertThat(model.uiState.value.input).isEqualTo("padma nadir majhi")
            assertThat(model.uiState.value.signedIn).isFalse()
            coVerify(exactly = 0) { assistant.ask(any(), any()) }

            user.value = me

            assertThat(model.uiState.value.signedIn).isTrue()
        }

    @Test
    fun `a summary for a seller is the person's own last three questions`() {
        val state =
            AssistantUiState(
                messages =
                    listOf("one", "two", "three", "four").flatMapIndexed { index, question ->
                        listOf(AssistantMessage(index * 2, true, question), AssistantMessage(index * 2 + 1, false, "answer"))
                    },
            )

        assertThat(summaryDraft("About \"Himu\". My questions so far:", state.lastQuestions()))
            .isEqualTo("About \"Himu\". My questions so far:\n- two\n- three\n- four")
    }

    // ---------------------------------------------------------------- the screen

    @Test
    fun `the screen opens with the notice, its link and the starting questions`() {
        var asked = ""
        var privacy = 0
        show(AssistantUiState(), noActions.copy(onAsk = { asked = it }, onOpenPrivacy = { privacy++ }))

        compose.onNodeWithText("AI assistant – may make mistakes.").assertExists()
        compose.onNodeWithText("How your questions are handled").performClick()
        compose.onNodeWithText("Track my order").performClick()

        assertThat(privacy).isEqualTo(1)
        assertThat(asked).isEqualTo("Track my order")
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").assertExists()
    }

    @Test
    fun `a book under an answer opens that book`() {
        var opened: Book? = null
        val book = Book(id = "b1", title = "Himu", author = "Humayun Ahmed", price = 300.0, stock = 2)
        show(answered(reply("Here it is.").copy(books = listOf(book))), noActions.copy(onOpenBook = { opened = it }))

        compose.onNodeWithText("Himu", substring = true).performClick()

        assertThat(opened).isEqualTo(book)
        compose.onNodeWithContentDescription("Assistant: Here it is.").assertExists()
        compose.onNodeWithContentDescription("You asked: Do you have Himu?").assertExists()
    }

    @Test
    fun `a seller hand-over asks a visitor to sign in, and sends nothing`() {
        var signIn = 0
        var chats = 0
        val handover = AssistantHandover(toSeller = true, chatWith = "seller@example.com", name = "rahim_books", bookTitle = "Himu", email = null)
        show(
            answered(reply("Ask the seller.").copy(handover = handover)),
            noActions.copy(onSignIn = { signIn++ }, onChat = { _, _ -> chats++ }),
        )

        compose.onNodeWithText("Want to ask rahim_books, the seller?").assertExists()
        compose.onNodeWithText("Chat with rahim_books").assertDoesNotExist()
        compose.onNodeWithText("Sign in to chat").performScrollTo().performClick()

        assertThat(signIn).isEqualTo(1)
        assertThat(chats).isEqualTo(0)
    }

    @Test
    fun `a signed-in buyer can take a summary along to the seller`() {
        var summary: Boolean? = null
        val handover = AssistantHandover(toSeller = true, chatWith = "seller@example.com", name = "rahim_books", bookTitle = "Himu", email = null)
        show(
            answered(reply("Ask the seller.").copy(handover = handover)).copy(signedIn = true),
            noActions.copy(onChat = { _, withSummary -> summary = withSummary }),
        )

        compose.onNodeWithText("Include a summary of this conversation").performScrollTo().performClick()
        compose.onNodeWithText("Chat with rahim_books").performScrollTo().performClick()

        assertThat(summary).isTrue()
    }

    @Test
    fun `the shop is reached by chat when it has an account, and by e-mail`() {
        var mailed = ""
        val handover = AssistantHandover(toSeller = false, chatWith = "shop@example.com", name = "BookStoreBD", bookTitle = null, email = "shop@example.com")
        show(answered(reply("Talk to the shop.").copy(handover = handover)).copy(signedIn = true), noActions.copy(onEmail = { mailed = it }))

        compose.onNodeWithText("Chat with BookStoreBD").assertExists()
        compose.onNodeWithText("E-mail the shop").performScrollTo().performClick()

        assertThat(mailed).isEqualTo("shop@example.com")
    }

    @Test
    fun `only links to the shop's own pages are offered`() {
        var opened = ""
        val links = listOf(AssistantLink("/filter?deals=1", "Deals"), AssistantLink("https://elsewhere.example", "Away"))
        // The mapper drops the second; a reply built by hand shows the screen has nothing else to go on.
        show(answered(reply("See the deals.").copy(links = links.filter { it.isSitePath })), noActions.copy(onOpenLink = { opened = it }))

        compose.onNodeWithText("Away").assertDoesNotExist()
        compose.onNodeWithText("Deals").performScrollTo().performClick()

        assertThat(opened).isEqualTo("/filter?deals=1")
    }

    @Test
    fun `a visitor over the limit is shown the server's words and offered to sign in`() {
        var signIn = 0
        show(
            AssistantUiState(input = "one more", error = AppError.RateLimited("That is all for today.")),
            noActions.copy(onSignIn = { signIn++ }),
        )

        compose.onNodeWithText("That is all for today.").assertExists()
        compose.onNodeWithText("Sign in for your own daily questions").performClick()
        compose.onNodeWithContentDescription("Send").assertIsEnabled()

        assertThat(signIn).isEqualTo(1)
    }

    @Test
    fun `thinking is said aloud, and the thumbs give way to thanks`() {
        show(answered(reply()).copy(pending = true))
        compose.onNodeWithText("Thinking…").assertExists()
        compose.onNodeWithContentDescription("This answer helped").assertExists()
        compose.onNodeWithContentDescription("This answer did not help").assertExists()
    }

    @Test
    fun `the screen holds together at the largest font size`() {
        val handover = AssistantHandover(toSeller = true, chatWith = "seller@example.com", name = "rahim_books", bookTitle = "Himu", email = null)
        val state = answered(reply().copy(handover = handover, wanted = AssistantWantedOffer("The Great Gatsby", ""))).copy(signedIn = true)
        show(state.copy(messages = state.messages.map { if (it.fromUser) it else it.copy(feedbackSent = true) }), fontScale = 2f)

        listOf("Chat with rahim_books", "Ask for \"The Great Gatsby\" on the Wanted board", "Thanks for the feedback").forEach { text ->
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Start over").assertExists()
    }
}
