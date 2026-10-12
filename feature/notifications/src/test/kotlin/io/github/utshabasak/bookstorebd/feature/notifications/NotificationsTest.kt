package io.github.utshabasak.bookstorebd.feature.notifications

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.inbox.LiveUpdates
import io.github.utshabasak.bookstorebd.core.data.inbox.NotificationRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.AppNotification
import io.github.utshabasak.bookstorebd.core.model.NotificationCategory
import io.github.utshabasak.bookstorebd.core.model.NotificationChoice
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationsTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val compose = createComposeRule()

    private val shipped =
        AppNotification(
            "n1",
            "order-status",
            "Your order has shipped",
            "Order ABC is on its way",
            "/order-tracking/ABC",
            false,
            "2026-10-11T10:00:00Z",
        )

    private fun listModel(repository: NotificationRepository) =
        NotificationsViewModel(
            repository,
            mockk<LiveUpdates> { every { updates } returns MutableSharedFlow() },
            mockk<UserSession> { every { user } returns flowOf(null) },
            mockk<ConfigRepository> { every { config } returns flowOf(null) },
        )

    @Test
    fun `opening one marks it read at once, and marking all clears the bell`() =
        runTest {
            val repository =
                mockk<NotificationRepository>(relaxed = true) {
                    every { unread } returns MutableStateFlow(3)
                    every { notifications(any()) } returns emptyFlow()
                    coEvery { markRead(null) } returns Outcome.Success(Unit)
                }
            val vm = listModel(repository)

            vm.opened(shipped)
            assertThat(vm.readMarks.value.isRead(shipped)).isTrue()
            coVerify { repository.markRead(listOf("n1")) }

            vm.markAllRead()
            assertThat(vm.readMarks.value.all).isTrue()
        }

    @Test
    fun `a switch that the server refuses is put back, with the reason`() =
        runTest {
            val orders = NotificationChoice(NotificationCategory.Orders, "Orders", "", emailAvailable = true, inApp = true, email = true)
            val repository =
                mockk<NotificationRepository>(relaxed = true) {
                    coEvery { choices() } returns Outcome.Success(listOf(orders))
                    coEvery { saveChoices(any()) } returns Outcome.Failure(AppError.Offline)
                }
            val vm = NotificationSettingsViewModel(repository)

            vm.failureFlow.test {
                vm.change(orders.copy(email = false))
                assertThat(awaitItem()).isEqualTo(AppError.Offline)
            }
            assertThat(vm.uiState.value.choices).containsExactly(orders)
        }

    @Test
    fun `the list shows what is unread, and opens one when tapped`() {
        var opened: AppNotification? = null
        val done = LoadState.NotLoading(endOfPaginationReached = true)
        val page = flowOf(PagingData.from(listOf(shipped), LoadStates(done, done, done)))
        compose.setContent {
            BookStoreTheme {
                NotificationsScreen(
                    signedIn = true,
                    unread = 1,
                    items = page.collectAsLazyPagingItems(),
                    marks = ReadMarks(),
                    actions = NotificationsActions({}, { opened = it }, {}, {}, {}),
                )
            }
        }

        compose.waitUntil { count("Your order has shipped") > 0 }
        compose.onNodeWithContentDescription("Unread").assertExists()
        compose.onNodeWithContentDescription("Mark all as read").assertExists()
        compose.onNodeWithText("Your order has shipped").performClick()
        assertThat(opened).isEqualTo(shipped)
    }

    @Test
    fun `each kind has its own switches, in the app's words`() {
        var changed: NotificationChoice? = null
        val deals = NotificationChoice(NotificationCategory.Deals, "Deals", "", emailAvailable = false, inApp = true, email = true)
        val returns = NotificationChoice(NotificationCategory.Returns, "Returns", "", emailAvailable = true, inApp = true, email = false)
        compose.setContent {
            BookStoreTheme {
                NotificationSettingsScreen(
                    state = SettingsUiState(choices = listOf(deals, returns), loading = false),
                    actions = NotificationSettingsActions({}, { changed = it }, {}, {}),
                )
            }
        }

        compose.onNodeWithText("Deals and price drops").assertExists()
        compose.onNodeWithText("By e-mail").assertIsOff()
        assertThat(count("In the app")).isEqualTo(2)
        compose.onNodeWithText("By e-mail").performClick()
        assertThat(changed).isEqualTo(returns.copy(email = true))
        compose.onNodeWithText("Returns").assertExists()
        compose.onAllNodes(hasText("In the app"))[0].assertIsOn()
    }

    @Test
    fun `Unread only asks the server for just the unread ones`() =
        runTest {
            val repository =
                mockk<NotificationRepository>(relaxed = true) {
                    every { unread } returns MutableStateFlow(2)
                    every { notifications(any()) } returns flowOf(PagingData.empty())
                }
            val vm = listModel(repository)
            val watching = launch { vm.items.collect {} }

            vm.setUnreadOnly(true)
            advanceUntilIdle()

            verify { repository.notifications(true) }
            watching.cancel()
        }

    @Test
    fun `the list says how many are unread and has the Unread only filter`() {
        var only: Boolean? = null
        val done = LoadState.NotLoading(endOfPaginationReached = true)
        val page = flowOf(PagingData.from(listOf(shipped), LoadStates(done, done, done)))
        compose.setContent {
            BookStoreTheme {
                NotificationsScreen(
                    signedIn = true,
                    unread = 3,
                    items = page.collectAsLazyPagingItems(),
                    marks = ReadMarks(),
                    actions = NotificationsActions({}, {}, {}, {}, {}, onUnreadOnly = { only = it }),
                )
            }
        }

        compose.waitUntil { count("3 unread") > 0 }
        compose.onNodeWithText("Unread only").performClick()
        assertThat(only).isTrue()
    }

    private fun count(text: String): Int = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().size
}
