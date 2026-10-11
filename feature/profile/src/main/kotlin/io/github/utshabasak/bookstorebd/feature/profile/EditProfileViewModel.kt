package io.github.utshabasak.bookstorebd.feature.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.image.ImagePreparer
import io.github.utshabasak.bookstorebd.core.data.image.ImagePurpose
import io.github.utshabasak.bookstorebd.core.data.repository.AuthRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ProfileRepository
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Gender
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.OwnProfile
import io.github.utshabasak.bookstorebd.core.model.PasswordCheck
import io.github.utshabasak.bookstorebd.core.model.PictureChange
import io.github.utshabasak.bookstorebd.core.model.ProfileChanges
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the form holds, as the person types. */
data class ProfileForm(
    val username: String = "",
    val address: String = "",
    val phone: String = "",
    val bkash: String = "",
    val dateOfBirth: String = "",
    val gender: Gender? = null,
    val newPassword: String = "",
    val currentPassword: String = "",
)

/** A picture as the form shows it: the saved one, a new choice, or none. */
data class PictureState(
    val savedUrl: String?,
    val change: PictureChange = PictureChange.Keep,
    val preview: Uri? = null,
) {
    val shownUrl: Any? get() = preview ?: savedUrl.takeIf { change != PictureChange.Remove }
    val hasPicture: Boolean get() = shownUrl != null
}

enum class FormField { Username, Phone, Bkash, NewPassword, CurrentPassword }

data class EditProfileUiState(
    val loaded: Boolean = false,
    val email: String = "",
    val form: ProfileForm = ProfileForm(),
    /** The form as it was loaded, to tell whether anything changed. */
    val savedForm: ProfileForm = ProfileForm(),
    val picture: PictureState = PictureState(null),
    val buyerBanner: PictureState = PictureState(null),
    val sellerBanner: PictureState = PictureState(null),
    /** False for a Google account that has never chosen a password: no current password is asked for. */
    val passwordSet: Boolean = true,
    val savedBkash: String = "",
    val passwordCheck: PasswordCheck? = null,
    val problems: Map<FormField, String?> = emptyMap(),
    val saving: Boolean = false,
    val preparingPicture: Boolean = false,
) {
    val hasChanges: Boolean
        get() =
            form != savedForm ||
                listOf(picture, buyerBanner, sellerBanner).any { it.change != PictureChange.Keep }

    /** The current password is asked for only when a change needs it, as on the website. */
    val needsCurrentPassword: Boolean
        get() = passwordSet && (form.newPassword.isNotEmpty() || (savedBkash.isNotBlank() && normaliseBd(form.bkash) != savedBkash))
}

sealed interface EditProfileEvent {
    data object Saved : EditProfileEvent

    data class Failed(
        val error: AppError,
    ) : EditProfileEvent
}

/** The edit form: details, pictures, and a new password, saved in one go as the website does. */
@OptIn(FlowPreview::class)
@HiltViewModel
class EditProfileViewModel
    @Inject
    constructor(
        private val profiles: ProfileRepository,
        private val auth: AuthRepository,
        private val images: ImagePreparer,
    ) : ViewModel() {
        private val state = MutableStateFlow(EditProfileUiState())
        val uiState: StateFlow<EditProfileUiState> = state.asStateFlow()

        private val events = Channel<EditProfileEvent>(Channel.BUFFERED)
        val eventFlow: Flow<EditProfileEvent> = events.receiveAsFlow()

        init {
            viewModelScope.launch {
                profiles.profile.filterNotNull().first().let(::fill)
            }
            viewModelScope.launch {
                when (val fresh = profiles.refresh()) {
                    is Outcome.Success -> if (!state.value.loaded) fill(fresh.value)
                    is Outcome.Failure -> if (!state.value.loaded) events.send(EditProfileEvent.Failed(fresh.error))
                }
            }
            viewModelScope.launch {
                state
                    .map { it.form.newPassword }
                    .distinctUntilChanged()
                    .debounce(PASSWORD_CHECK_DELAY_MS)
                    .collect { password ->
                        if (password.isEmpty()) {
                            state.update { it.copy(passwordCheck = null) }
                        } else {
                            val current = state.value
                            (auth.checkPassword(password, current.email, current.form.username) as? Outcome.Success)?.let { check ->
                                state.update { it.copy(passwordCheck = check.value) }
                            }
                        }
                    }
            }
        }

        private fun fill(profile: OwnProfile) {
            state.update {
                it.copy(
                    loaded = true,
                    email = profile.email,
                    form = loadedForm(profile),
                    savedForm = loadedForm(profile),
                    picture = PictureState(profile.pictureUrl),
                    buyerBanner = PictureState(profile.buyerBannerUrl),
                    sellerBanner = PictureState(profile.sellerBannerUrl),
                    passwordSet = profile.passwordSet,
                    savedBkash = profile.bkashMerchant,
                )
            }
        }

        private fun loadedForm(profile: OwnProfile) =
            ProfileForm(
                username = profile.username,
                address = profile.address,
                phone = profile.phone,
                bkash = profile.bkashMerchant,
                dateOfBirth = profile.dateOfBirth,
                gender = profile.gender,
            )

        fun edit(change: (ProfileForm) -> ProfileForm) {
            state.update { it.copy(form = change(it.form), problems = emptyMap()) }
        }

        fun choosePicture(
            slot: PictureSlot,
            uri: Uri,
        ) {
            viewModelScope.launch {
                state.update { it.copy(preparingPicture = true) }
                val purpose = if (slot == PictureSlot.Photo) ImagePurpose.Avatar else ImagePurpose.Banner
                val prepared = images.prepare(uri, purpose)
                state.update { current ->
                    val next = current.copy(preparingPicture = false)
                    if (prepared == null) {
                        next
                    } else {
                        next.withPicture(slot) { it.copy(change = PictureChange.Replace(prepared), preview = uri) }
                    }
                }
            }
        }

        fun removePicture(slot: PictureSlot) {
            state.update { it.withPicture(slot) { picture -> picture.copy(change = PictureChange.Remove, preview = null) } }
        }

        private fun EditProfileUiState.withPicture(
            slot: PictureSlot,
            change: (PictureState) -> PictureState,
        ) = when (slot) {
            PictureSlot.Photo -> copy(picture = change(picture))
            PictureSlot.BuyerBanner -> copy(buyerBanner = change(buyerBanner))
            PictureSlot.SellerBanner -> copy(sellerBanner = change(sellerBanner))
        }

        fun save() {
            val current = state.value
            if (current.saving) return
            val problems = validate(current)
            if (problems.isNotEmpty()) {
                state.update { it.copy(problems = problems) }
                return
            }
            viewModelScope.launch {
                state.update { it.copy(saving = true) }
                val form = current.form
                val result =
                    profiles.update(
                        ProfileChanges(
                            username = form.username,
                            newPassword = form.newPassword,
                            currentPassword = if (current.needsCurrentPassword) form.currentPassword else "",
                            address = form.address,
                            phone = form.phone,
                            bkashMerchant = if (form.bkash.isBlank()) "" else normaliseBd(form.bkash),
                            dateOfBirth = form.dateOfBirth,
                            gender = form.gender,
                            picture = current.picture.change,
                            buyerBanner = current.buyerBanner.change,
                            sellerBanner = current.sellerBanner.change,
                        ),
                    )
                state.update { it.copy(saving = false) }
                when (result) {
                    is Outcome.Success -> {
                        events.send(EditProfileEvent.Saved)
                    }

                    is Outcome.Failure -> {
                        val rejected = result.error as? AppError.Rejected
                        when {
                            rejected?.code == "current-password" -> {
                                state.update { it.copy(problems = mapOf(FormField.CurrentPassword to rejected.message)) }
                            }

                            rejected?.status == HTTP_CONFLICT -> {
                                state.update { it.copy(problems = mapOf(FormField.Username to rejected.message)) }
                            }

                            else -> {
                                events.send(EditProfileEvent.Failed(result.error))
                            }
                        }
                    }
                }
            }
        }

        private fun validate(current: EditProfileUiState): Map<FormField, String?> =
            buildMap {
                val form = current.form
                if (form.username.trim().length !in USERNAME_LENGTH) put(FormField.Username, null)
                if (form.phone.isNotBlank() && !isBdMobile(normaliseBd(form.phone))) put(FormField.Phone, null)
                if (form.bkash.isNotBlank() && !isBdMobile(normaliseBd(form.bkash))) put(FormField.Bkash, null)
                if (form.newPassword.isNotEmpty() && current.passwordCheck?.ok == false) put(FormField.NewPassword, null)
                if (current.needsCurrentPassword && form.currentPassword.isEmpty()) put(FormField.CurrentPassword, null)
            }

        private companion object {
            const val PASSWORD_CHECK_DELAY_MS = 400L
            const val HTTP_CONFLICT = 409
            val USERNAME_LENGTH = 3..40
        }
    }

enum class PictureSlot { Photo, BuyerBanner, SellerBanner }

/** A Bangladeshi mobile number in its usual form, 01XXXXXXXXX: spaces, dashes and +880 removed. */
internal fun normaliseBd(value: String): String {
    val digits = value.filter(Char::isDigit)
    return if (digits.startsWith("880")) digits.removePrefix("88") else digits
}

internal fun isBdMobile(value: String): Boolean = Regex("^01[3-9]\\d{8}$").matches(value)
