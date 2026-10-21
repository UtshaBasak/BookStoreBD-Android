package io.github.utshabasak.bookstorebd.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.common.AppBuildInfo
import io.github.utshabasak.bookstorebd.core.data.repository.ApkInstaller
import io.github.utshabasak.bookstorebd.core.data.repository.AuthRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ProfileRepository
import io.github.utshabasak.bookstorebd.core.data.repository.SettingsRepository
import io.github.utshabasak.bookstorebd.core.data.repository.UpdateRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.AppRelease
import io.github.utshabasak.bookstorebd.core.model.CodeAction
import io.github.utshabasak.bookstorebd.core.model.IdentityCheck
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.OwnProfile
import io.github.utshabasak.bookstorebd.core.model.ProfileMode
import io.github.utshabasak.bookstorebd.core.model.Rating
import io.github.utshabasak.bookstorebd.core.model.SessionUser
import io.github.utshabasak.bookstorebd.core.model.SiteLinks
import io.github.utshabasak.bookstorebd.core.model.ThemePreference
import io.github.utshabasak.bookstorebd.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SettingsTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val compose = createComposeRule()

    private val release =
        AppRelease("1.2.0", 10200, "https://github.com/x.apk", 4_000_000, "a".repeat(64), "Faster search.", "https://github.com/x")
    private val build =
        AppBuildInfo(versionName = "1.1.0", versionCode = 10100, selfUpdate = true, releasesUrl = "https://github.com/x/releases")

    private fun updates(newer: AppRelease? = release) =
        mockk<UpdateRepository>(relaxed = true) {
            every { newerRelease } returns flowOf(newer)
            every { updateRequired } returns flowOf(newer != null)
            coEvery { check() } returns Outcome.Success(newer)
        }

    private val installer =
        mockk<ApkInstaller>(relaxed = true) { every { canInstall } returns true }

    @Test
    fun `downloads a newer release, checks it, and hands it to the installer`() =
        runTest {
            val apk = File("BookStoreBD-1.2.0.apk")
            val repo = updates()
            coEvery { repo.download(release, any()) } returns Outcome.Success(apk)
            val vm = UpdateViewModel(repo, installer, build)

            vm.uiState.test {
                assertThat(expectMostRecentItem().newer).isEqualTo(release)
                vm.download(release)
                assertThat(expectMostRecentItem().status).isEqualTo(UpdateStatus.Ready(apk))
            }
            coVerify { installer.install(apk) }
        }

    @Test
    fun `a download that fails its checksum is not installed`() =
        runTest {
            val repo = updates()
            val mismatch = AppError.Rejected(status = 0, message = null, code = "checksum-mismatch")
            coEvery { repo.download(release, any()) } returns Outcome.Failure(mismatch)
            val vm = UpdateViewModel(repo, installer, build)

            vm.uiState.test {
                skipItems(1)
                vm.download(release)
                assertThat(expectMostRecentItem().status).isEqualTo(UpdateStatus.Failed(mismatch))
            }
            coVerify(exactly = 0) { installer.install(any()) }
        }

    @Test
    fun `a newer release locks the app and starts downloading by itself, once`() =
        runTest {
            val apk = File("BookStoreBD-1.2.0.apk")
            val repo = updates()
            coEvery { repo.download(release, any()) } returns Outcome.Success(apk)
            val vm = UpdateViewModel(repo, installer, build)

            vm.uiState.test {
                vm.checkAndFetch()
                vm.checkAndFetch()
                val state = expectMostRecentItem()
                assertThat(state.required).isTrue()
                assertThat(state.status).isEqualTo(UpdateStatus.Ready(apk))
            }
            coVerify(exactly = 1) { repo.download(release, any()) }
            coVerify(exactly = 1) { installer.install(apk) }
        }

    @Test
    fun `checking with no answer from anywhere says so instead of up to date`() =
        runTest {
            val repo = updates(newer = null)
            coEvery { repo.check() } returns Outcome.Failure(AppError.Offline)
            val vm = UpdateViewModel(repo, installer, build)

            vm.uiState.test {
                vm.checkNow()
                assertThat(expectMostRecentItem().status).isEqualTo(UpdateStatus.Failed(AppError.Offline))
            }
        }

    @Test
    fun `the Play build never checks or installs updates itself`() =
        runTest {
            val repo = updates()
            val vm = UpdateViewModel(repo, installer, build.copy(selfUpdate = false))
            vm.checkAndFetch()
            vm.uiState.test {
                assertThat(expectMostRecentItem().canInstall).isFalse()
            }
            coVerify(exactly = 0) { repo.check() }
            coVerify(exactly = 0) { repo.download(any(), any()) }
        }

    @Test
    fun `the update screen shows the new version and installs it`() {
        var installed: AppRelease? = null
        compose.setContent {
            BookStoreTheme {
                UpdateRequiredScreen(
                    release = release,
                    status = UpdateStatus.Idle,
                    canInstall = true,
                    onInstall = { installed = it },
                    onOpenReleases = {},
                    onOpenWebsite = {},
                )
            }
        }
        compose.onNodeWithText("Version 1.2.0 is out", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Faster search.").assertIsDisplayed()
        compose.onNodeWithText("Install").performClick()
        assertThat(installed).isEqualTo(release)
    }

    @Test
    fun `the account screen shows the chosen theme and offers the update`() {
        var downloaded: AppRelease? = null
        compose.setContent {
            BookStoreTheme {
                AccountScreen(
                    state = AccountUiState(sessionKnown = true, theme = ThemePreference.Dark),
                    update = UpdateUiState(currentVersion = "1.1.0", newer = release, canInstall = true),
                    actions = AccountActions({}, {}, {}, {}, {}, {}, {}, { downloaded = it }, {}),
                )
            }
        }
        compose.onNodeWithText("You are not signed in").assertIsDisplayed()
        compose.onNodeWithText("Dark").assertIsSelected()
        compose.onNodeWithText("Version 1.2.0 is available").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Install").performClick()
        assertThat(downloaded).isEqualTo(release)
    }

    @Test
    fun `an account made with Google turns two-step sign-in off with an e-mailed code`() =
        runTest {
            val profiles =
                mockk<ProfileRepository>(relaxed = true) {
                    every { profile } returns flowOf(null)
                    coEvery { sendCode(CodeAction.TwoStepOff) } returns Outcome.Success("We have e-mailed you a code.")
                    coEvery { setTwoStep(false, IdentityCheck(code = "000111")) } returns
                        Outcome.Failure(AppError.Rejected(400, "That code is not correct."))
                }
            val settings =
                mockk<SettingsRepository>(relaxed = true) {
                    every { theme } returns flowOf(ThemePreference.System)
                    every { profileMode } returns flowOf(ProfileMode.Buyer.name)
                }
            val vm =
                AccountViewModel(
                    mockk<UserSession> { every { user } returns flowOf(SessionUser("1", "n", "n@example.com", false)) },
                    settings,
                    mockk<AuthRepository>(relaxed = true),
                    mockk<ConfigRepository> { every { config } returns flowOf(null) },
                    profiles,
                )
            vm.uiState.test {
                vm.sendTwoStepCode()
                assertThat(expectMostRecentItem().twoStepCode.sent).isEqualTo("We have e-mailed you a code.")
                vm.setTwoStep(false, IdentityCheck(code = "000111"))
                val refused = expectMostRecentItem()
                assertThat(refused.twoStepProblem).isEqualTo("That code is not correct.")
                // The code already sent stays, so a corrected one can be typed.
                assertThat(refused.twoStepCode.sent).isEqualTo("We have e-mailed you a code.")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the seller side shows the shop's rows and where sales are paid, and the buyer side does not`() {
        var mode: ProfileMode? = null
        val profile =
            OwnProfile(
                username = "nusrat",
                email = "nusrat@example.com",
                pictureUrl = null,
                buyerBannerUrl = null,
                sellerBannerUrl = null,
                sellerRating = Rating(),
                address = "12 Road, Dhaka",
                phone = "",
                bkashMerchant = "01712345678",
                dateOfBirth = "",
                gender = null,
                twoFactor = false,
                passwordSet = true,
            )
        val links =
            SiteLinks("https://shop.test", "p", "t", "r", "h", about = "a", contact = "c", email = "help@shop.test", location = "Dhaka")
        var state by mutableStateOf(
            AccountUiState(
                user = SessionUser("1", "nusrat", "nusrat@example.com", false),
                sessionKnown = true,
                profile = profile,
                links = links,
            ),
        )
        compose.setContent {
            BookStoreTheme {
                AccountScreen(
                    state = state,
                    update = UpdateUiState(currentVersion = "1.1.0"),
                    actions = AccountActions({}, {}, {}, {}, {}, {}, {}, {}, {}, onMode = { mode = it }),
                )
            }
        }

        compose.onNodeWithText("12 Road, Dhaka").assertIsDisplayed()
        compose.onNodeWithText("Orders for my books").assertDoesNotExist()
        compose.onNodeWithText("Seller profile").performClick()
        assertThat(mode).isEqualTo(ProfileMode.Seller)

        state = state.copy(mode = ProfileMode.Seller)
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Paid to bKash: 01712345678"))
        compose.onNodeWithText("Paid to bKash: 01712345678").assertIsDisplayed()
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Orders for my books"))
        compose.onNodeWithText("Orders for my books").assertIsDisplayed()
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("help@shop.test"))
        compose.onNodeWithText("help@shop.test").assertIsDisplayed()
    }
}
