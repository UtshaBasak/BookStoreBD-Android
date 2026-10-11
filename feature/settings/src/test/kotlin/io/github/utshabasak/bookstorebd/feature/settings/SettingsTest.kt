package io.github.utshabasak.bookstorebd.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.common.AppBuildInfo
import io.github.utshabasak.bookstorebd.core.data.repository.ApkInstaller
import io.github.utshabasak.bookstorebd.core.data.repository.UpdateRepository
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.AppRelease
import io.github.utshabasak.bookstorebd.core.model.Outcome
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

    private fun updates(offered: AppRelease? = release) =
        mockk<UpdateRepository>(relaxed = true) {
            every { newerRelease } returns flowOf(offered)
            every { offeredRelease } returns flowOf(offered)
            every { updateRequired } returns flowOf(false)
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
                assertThat(expectMostRecentItem().offered).isEqualTo(release)
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
    fun `the Play build never offers to install updates itself`() =
        runTest {
            val vm = UpdateViewModel(updates(), installer, build.copy(selfUpdate = false))
            vm.uiState.test {
                val state = expectMostRecentItem()
                assertThat(state.offered).isNull()
                assertThat(state.canInstall).isFalse()
            }
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
}
