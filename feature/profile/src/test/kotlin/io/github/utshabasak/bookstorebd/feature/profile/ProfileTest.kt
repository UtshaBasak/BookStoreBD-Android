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
import io.github.utshabasak.bookstorebd.core.model.CodeAction
import io.github.utshabasak.bookstorebd.core.model.Gender
import io.github.utshabasak.bookstorebd.core.model.IdentityCheck
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
    fun `a bKash number still in use is refused on its own field`() =
        runTest {
            coEvery { profiles.update(any()) } returns
                Outcome.Failure(
                    AppError.Rejected(409, "Your bKash number is still needed for books on sale.", "payout-number-in-use", emptyMap()),
                )
            val vm = editor()
            vm.edit { it.copy(bkash = "", currentPassword = "Tangerine-Lantern-42!") }

            vm.save()

            assertThat(vm.uiState.value.problems[FormField.Bkash]).isEqualTo("Your bKash number is still needed for books on sale.")
            assertThat(vm.uiState.value.problems).doesNotContainKey(FormField.Username)
        }

    @Test
    fun `the e-mail address moves only once the code from the new address is given`() =
        runTest {
            coEvery { profiles.changeEmail("new@example.com", "Tangerine-Lantern-42!") } returns
                Outcome.Success("We have e-mailed a code to new@example.com.")
            coEvery { profiles.confirmEmail("123456") } returns Outcome.Success("Your account now uses new@example.com.")
            val vm = editor()

            vm.openEmailChange()
            vm.editEmailChange { it.copy(newEmail = "new@example.com", password = "Tangerine-Lantern-42!") }
            vm.eventFlow.test {
                vm.startEmailChange()
                assertThat(awaitItem()).isEqualTo(EditProfileEvent.Message("We have e-mailed a code to new@example.com."))
                assertThat(vm.uiState.value.emailChange.waitingFor).isEqualTo("new@example.com")
                assertThat(vm.uiState.value.email).isEqualTo("nusrat@example.com")

                vm.editEmailChange { it.copy(code = "12345") }
                assertThat(vm.uiState.value.emailChange.canConfirm).isFalse()
                vm.editEmailChange { it.copy(code = "123456") }
                vm.confirmEmailChange()
                assertThat(awaitItem()).isEqualTo(EditProfileEvent.Message("Your account now uses new@example.com."))
            }
            assertThat(vm.uiState.value.email).isEqualTo("new@example.com")
            assertThat(vm.uiState.value.emailChange.open).isFalse()
        }

    @Test
    fun `an account made with Google is asked to choose a password before changing its address`() {
        compose.setContent {
            BookStoreTheme {
                EmailChange(
                    currentEmail = "nusrat@example.com",
                    passwordSet = false,
                    state = EmailChangeState(open = true),
                    actions = EmailChangeActions({}, {}, {}, {}, {}, {}),
                )
            }
        }

        compose.onNodeWithText("New e-mail address").assertDoesNotExist()
        compose.onNodeWithText("Choose a password below", substring = true).assertExists()
    }

    @Test
    fun `closing an account says what has to finish first, and an account with no password confirms with a code`() =
        runTest {
            coEvery { profiles.sendCode(CodeAction.DeleteAccount) } returns Outcome.Success("We have e-mailed you a code.")
            coEvery { profiles.deleteAccount(IdentityCheck(code = "654321")) } returns
                Outcome.Failure(
                    AppError.Rejected(
                        409,
                        "Your account cannot be closed yet.",
                        "account-has-open-business",
                        blockers = listOf("Order BS-1 is on its way."),
                    ),
                )
            every { profiles.profile } returns flowOf(profile.copy(passwordSet = false))
            val settings =
                mockk<SettingsRepository>(relaxed = true) {
                    every { rememberSearches } returns flowOf(true)
                    every { rememberViewed } returns flowOf(true)
                }
            val vm =
                PrivacyViewModel(
                    settings,
                    mockk<CatalogueRepository>(relaxed = true),
                    profiles,
                    mockk<UserSession> { every { user } returns flowOf(null) },
                    mockk<ConfigRepository> { every { config } returns flowOf(null) },
                )
            vm.uiState.test {
                vm.sendCode()
                vm.delete(IdentityCheck(code = "654321"))
                val state = expectMostRecentItem()
                assertThat(state.passwordSet).isFalse()
                assertThat(state.code.sent).isEqualTo("We have e-mailed you a code.")
                assertThat(
                    state.blocked,
                ).isEqualTo(AccountBlocked("Your account cannot be closed yet.", listOf("Order BS-1 is on its way.")))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the reasons an account cannot close show on the page`() {
        compose.setContent {
            BookStoreTheme {
                PrivacyScreen(
                    state =
                        PrivacyUiState(
                            signedIn = true,
                            blocked = AccountBlocked("Your account cannot be closed yet.", listOf("Order BS-1 is on its way.")),
                        ),
                    actions = PrivacyActions({}, {}, {}, {}, {}, {}, {}),
                )
            }
        }

        compose.onNodeWithText("Your account cannot be closed yet.").assertExists()
        compose.onNodeWithText("• Order BS-1 is on its way.").assertExists()
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
