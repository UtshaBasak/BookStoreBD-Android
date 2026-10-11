package io.github.utshabasak.bookstorebd.feature.profile

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.image.ImagePreparer
import io.github.utshabasak.bookstorebd.core.data.repository.AuthRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CatalogueRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ProfileRepository
import io.github.utshabasak.bookstorebd.core.data.repository.SettingsRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Gender
import io.github.utshabasak.bookstorebd.core.model.Invites
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.OwnProfile
import io.github.utshabasak.bookstorebd.core.model.ProfileChanges
import io.github.utshabasak.bookstorebd.core.model.Rating
import io.github.utshabasak.bookstorebd.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val compose = createComposeRule()

    private val profile =
        OwnProfile(
            username = "nusrat",
            email = "nusrat@example.com",
            pictureUrl = null,
            buyerBannerUrl = null,
            sellerBannerUrl = null,
            sellerRating = Rating(),
            address = "12 Road",
            phone = "01712345678",
            bkashMerchant = "01812345678",
            dateOfBirth = "",
            gender = Gender.Female,
            twoFactor = false,
            passwordSet = true,
        )

    private val profiles =
        mockk<ProfileRepository>(relaxed = true) {
            every { profile } returns flowOf(this@ProfileTest.profile)
            coEvery { refresh() } returns Outcome.Success(this@ProfileTest.profile)
        }

    private fun editor() = EditProfileViewModel(profiles, mockk<AuthRepository>(relaxed = true), mockk<ImagePreparer>(relaxed = true))

    @Test
    fun `fills the form from the profile, and asks for the password only when a change needs it`() =
        runTest {
            val vm = editor()

            assertThat(vm.uiState.value.form.username).isEqualTo("nusrat")
            assertThat(vm.uiState.value.needsCurrentPassword).isFalse()

            vm.edit { it.copy(bkash = "01912345678") }
            assertThat(vm.uiState.value.needsCurrentPassword).isTrue()
            assertThat(vm.uiState.value.hasChanges).isTrue()
        }

    @Test
    fun `checks the phone and the current password before saving`() =
        runTest {
            val vm = editor()
            vm.edit { it.copy(phone = "12345", bkash = "01912345678") }

            vm.save()

            assertThat(vm.uiState.value.problems.keys).containsExactly(FormField.Phone, FormField.CurrentPassword)
            coVerify(exactly = 0) { profiles.update(any()) }
        }

    @Test
    fun `saves a normalised bKash number, and shows a wrong current password on its field`() =
        runTest {
            val sent = slot<ProfileChanges>()
            coEvery { profiles.update(capture(sent)) } returns
                Outcome.Failure(AppError.Rejected(403, "Your current password is not correct", "current-password", emptyMap()))
            val vm = editor()
            vm.edit { it.copy(bkash = "+880 1912-345678", currentPassword = "old-password") }

            vm.save()

            assertThat(sent.captured.bkashMerchant).isEqualTo("01912345678")
            assertThat(sent.captured.currentPassword).isEqualTo("old-password")
            assertThat(vm.uiState.value.problems[FormField.CurrentPassword]).isEqualTo("Your current password is not correct")
        }

    @Test
    fun `invitations allow at most five addresses and no links in the note`() =
        runTest {
            coEvery { profiles.invites() } returns Outcome.Success(Invites(emptyList(), remainingToday = 20))
            val vm = InviteViewModel(profiles)

            vm.setEmails("a@x.com, b@x.com, c@x.com, d@x.com, e@x.com, f@x.com")
            vm.send()
            assertThat(vm.uiState.value.problem).isEqualTo(InviteProblem.TooMany)

            vm.setEmails("a@x.com\nb@x.com")
            vm.setNote("See www.example.com")
            vm.send()
            assertThat(vm.uiState.value.problem).isEqualTo(InviteProblem.LinkInNote)

            coEvery { profiles.invite(listOf("a@x.com", "b@x.com"), "Come and see") } returns Outcome.Success("Invitations sent")
            vm.setNote("Come and see")
            vm.eventFlow.test {
                vm.send()
                assertThat(awaitItem()).isEqualTo(InviteEvent.Sent("Invitations sent"))
            }
        }

    @Test
    fun `turning off remembered searches also forgets them`() =
        runTest {
            val settings = mockk<SettingsRepository>(relaxed = true)
            val catalogue = mockk<CatalogueRepository>(relaxed = true)
            val vm =
                PrivacyViewModel(settings, catalogue, profiles, mockk<UserSession>(relaxed = true), mockk<ConfigRepository>(relaxed = true))

            vm.setRememberSearches(false)

            coVerify { settings.setRememberSearches(false) }
            coVerify { catalogue.clearRecentSearches() }
        }

    @Test
    fun `the privacy screen shows each choice as a switch`() {
        var searches: Boolean? = null
        compose.setContent {
            BookStoreTheme {
                PrivacyScreen(
                    state = PrivacyUiState(rememberSearches = true, rememberViewed = false, signedIn = true),
                    actions = PrivacyActions({}, { searches = it }, {}, {}, {}, {}, {}),
                )
            }
        }

        compose.onNodeWithText("Remember my searches").assertIsOn()
        compose.onNodeWithText("Remember books I open").assertIsOff()
        compose.onNodeWithText("Download my data").assertExists()
        compose.onNodeWithText("Remember my searches").performClick()
        assertThat(searches).isFalse()
    }
}
