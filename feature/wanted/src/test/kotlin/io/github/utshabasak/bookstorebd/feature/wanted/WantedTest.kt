package io.github.utshabasak.bookstorebd.feature.wanted

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.repository.CommunityRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.SessionUser
import io.github.utshabasak.bookstorebd.core.model.WantedDraft
import io.github.utshabasak.bookstorebd.core.model.WantedItem
import io.github.utshabasak.bookstorebd.core.model.WantedResult
import io.github.utshabasak.bookstorebd.core.model.WantedStatus
import io.github.utshabasak.bookstorebd.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class WantedTest {
    @get:Rule val main = MainDispatcherRule()

    private val item = WantedItem("w1", "Aparajito", "", "", "", 3, false, WantedStatus.Open, null, null, null)

    private fun session(signedIn: Boolean) =
        mockk<UserSession> { every { user } returns flowOf(if (signedIn) SessionUser("u1", "nusrat", "n@example.com", false) else null) }

    @Test
    fun `opens with the request form filled in from a search that found nothing`() {
        val vm = WantedViewModel("Aparajito", mockk(relaxed = true), session(signedIn = true))

        assertThat(vm.uiState.value.form).isEqualTo(WantedDraft(title = "Aparajito"))
    }

    @Test
    fun `asking needs an account, and a title of two letters or more`() =
        runTest {
            val community = mockk<CommunityRepository>(relaxed = true)
            val signedOut = WantedViewModel(null, community, session(signedIn = false))
            signedOut.eventFlow.test {
                signedOut.openForm()
                assertThat(awaitItem()).isEqualTo(WantedEvent.SignInNeeded)
            }

            coEvery { community.askForBook(any()) } returns Outcome.Success(WantedResult.Created("Added to the board"))
            val vm = WantedViewModel(null, community, session(signedIn = true))
            vm.openForm()
            vm.editForm { it.copy(title = "A") }
            vm.ask()
            assertThat(vm.uiState.value.titleProblem).isTrue()

            vm.editForm { it.copy(title = "Aparajito") }
            vm.eventFlow.test {
                vm.ask()
                assertThat(awaitItem()).isEqualTo(WantedEvent.Message("Added to the board"))
            }
            assertThat(vm.uiState.value.form).isNull()
        }

    @Test
    fun `joining shows at once, and is undone if the server says no`() =
        runTest {
            val community = mockk<CommunityRepository>(relaxed = true) { coEvery { join("w1") } returns Outcome.Failure(AppError.Offline) }
            val vm = WantedViewModel(null, community, session(signedIn = true))

            vm.eventFlow.test {
                vm.toggle(item)
                assertThat(awaitItem()).isEqualTo(WantedEvent.Failed(AppError.Offline))
            }
            coVerify { community.join("w1") }
            assertThat(vm.uiState.value.mine["w1"]).isFalse()
        }
}
