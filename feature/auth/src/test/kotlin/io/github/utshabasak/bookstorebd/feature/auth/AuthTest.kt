package io.github.utshabasak.bookstorebd.feature.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.repository.AuthRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppConfig
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.DeliveryRules
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.PasswordCheck
import io.github.utshabasak.bookstorebd.core.model.SessionUser
import io.github.utshabasak.bookstorebd.core.model.SignInOutcome
import io.github.utshabasak.bookstorebd.core.model.SiteLinks
import io.github.utshabasak.bookstorebd.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class AuthTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val compose = createComposeRule()

    private val user = SessionUser("u1", "nusrat", "nusrat@example.com", false)

    private fun config(captcha: Boolean) =
        AppConfig(
            minVersionCode = 0,
            latestRelease = null,
            captchaSiteKey = if (captcha) "site-key" else null,
            challengeUrl = if (captcha) "https://shop.example/app/challenge" else null,
            googleClientId = "web-client",
            delivery = DeliveryRules(70, 120, 3, 5),
            returnWindowDays = 7,
            categoryGroups = emptyList(),
            divisions = emptyList(),
            links = SiteLinks("", "", "", "", ""),
        )

    private val auth = mockk<AuthRepository>(relaxed = true)
    private val configs = mockk<ConfigRepository>()

    @Test
    fun `signs in straight away when the bot check is off`() =
        runTest {
            coEvery { configs.current() } returns config(captcha = false)
            coEvery { auth.signIn("nusrat@example.com", "Tangerine-Lantern-42!", null) } returns
                Outcome.Success(SignInOutcome.SignedIn(user))
            val vm = SignInViewModel(auth, configs)

            vm.onEmail(" nusrat@example.com ")
            vm.onPassword("Tangerine-Lantern-42!")
            vm.eventFlow.test {
                vm.submit()
                assertThat(awaitItem()).isEqualTo(SignInEvent.SignedIn)
            }
        }

    @Test
    fun `asks for the bot check first when it is on, then sends its token`() =
        runTest {
            coEvery { configs.current() } returns config(captcha = true)
            coEvery { auth.signIn(any(), any(), "turnstile-token") } returns Outcome.Success(SignInOutcome.SignedIn(user))
            val vm = SignInViewModel(auth, configs)
            vm.onEmail("nusrat@example.com")
            vm.onPassword("Tangerine-Lantern-42!")

            vm.submit()
            assertThat(vm.uiState.value.challengeUrl).isEqualTo("https://shop.example/app/challenge")
            coVerify(exactly = 0) { auth.signIn(any(), any(), any()) }

            vm.eventFlow.test {
                vm.onChallengeToken("turnstile-token")
                assertThat(awaitItem()).isEqualTo(SignInEvent.SignedIn)
            }
            assertThat(vm.uiState.value.challengeUrl).isNull()
        }

    @Test
    fun `asks for the e-mailed code when two-step sign-in is on`() =
        runTest {
            coEvery { configs.current() } returns config(captcha = false)
            coEvery { auth.signIn(any(), any(), any()) } returns
                Outcome.Success(SignInOutcome.CodeSent("nusrat@example.com", "n•••@example.com"))
            coEvery { auth.verifySignIn("nusrat@example.com", "123456") } returns Outcome.Success(SignInOutcome.SignedIn(user))
            val vm = SignInViewModel(auth, configs)
            vm.onEmail("nusrat@example.com")
            vm.onPassword("Tangerine-Lantern-42!")

            vm.submit()
            assertThat(vm.uiState.value.codeSentTo).isEqualTo("n•••@example.com")

            vm.onCode("12 34 56")
            vm.eventFlow.test {
                vm.submitCode()
                assertThat(awaitItem()).isEqualTo(SignInEvent.SignedIn)
            }
        }

    @Test
    fun `shows the server's reason when the details are wrong`() =
        runTest {
            coEvery { configs.current() } returns config(captcha = false)
            coEvery { auth.signIn(any(), any(), any()) } returns Outcome.Failure(AppError.Rejected(401, "Invalid email or password"))
            val vm = SignInViewModel(auth, configs)
            vm.onEmail("nusrat@example.com")
            vm.onPassword("wrong")
            vm.submit()
            assertThat(vm.uiState.value.error).isEqualTo(AppError.Rejected(401, "Invalid email or password"))
            assertThat(vm.uiState.value.busy).isFalse()
        }

    @Test
    fun `the sign-in form offers Google and labels the password toggle`() {
        compose.setContent {
            BookStoreTheme {
                SignInScreen(
                    state = SignInUiState(email = "nusrat@example.com", googleAvailable = true),
                    actions = SignInActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                )
            }
        }
        compose.onNodeWithText("Continue with Google").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show password").assertIsDisplayed()
        compose.onNodeWithText("Sign in").assertIsNotEnabled()
    }

    @Test
    fun `sign-up waits for a password that passes every rule`() {
        val vm = SignUpViewModel(auth, configs)
        compose.setContent {
            BookStoreTheme {
                SignUpScreen(
                    state =
                        SignUpUiState(
                            username = "nusrat",
                            email = "nusrat@example.com",
                            password = "short",
                            passwordCheck = PasswordCheck(ok = false, problems = listOf("length", "symbol"), message = ""),
                        ),
                    vm = vm,
                    onBack = {},
                    onSignIn = {},
                )
            }
        }
        compose.onNodeWithText("At least 12 characters — still needed").assertIsDisplayed()
        compose.onNodeWithText("Send code").assertIsNotEnabled()
    }

    @Test
    fun `sign-up is ready once the password passes`() {
        val vm = SignUpViewModel(auth, configs)
        compose.setContent {
            BookStoreTheme {
                SignUpScreen(
                    state =
                        SignUpUiState(
                            username = "nusrat",
                            email = "nusrat@example.com",
                            password = "Tangerine-Lantern-42!",
                            passwordCheck = PasswordCheck(ok = true, problems = emptyList(), message = ""),
                        ),
                    vm = vm,
                    onBack = {},
                    onSignIn = {},
                )
            }
        }
        compose.onNodeWithText("Send code").assertIsEnabled()
    }
}
