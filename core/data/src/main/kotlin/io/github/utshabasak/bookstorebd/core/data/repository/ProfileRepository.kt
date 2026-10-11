package io.github.utshabasak.bookstorebd.core.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.utshabasak.bookstorebd.core.common.BookStoreDispatchers
import io.github.utshabasak.bookstorebd.core.common.Dispatcher
import io.github.utshabasak.bookstorebd.core.data.cache.ResponseCache
import io.github.utshabasak.bookstorebd.core.data.session.SessionManager
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.ContactDetails
import io.github.utshabasak.bookstorebd.core.model.Invites
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.OwnProfile
import io.github.utshabasak.bookstorebd.core.model.PictureChange
import io.github.utshabasak.bookstorebd.core.model.ProfileChanges
import io.github.utshabasak.bookstorebd.core.model.map
import io.github.utshabasak.bookstorebd.core.network.AccountApi
import io.github.utshabasak.bookstorebd.core.network.AccountMappers
import io.github.utshabasak.bookstorebd.core.network.apiCall
import io.github.utshabasak.bookstorebd.core.network.dto.InviteRequest
import io.github.utshabasak.bookstorebd.core.network.dto.OwnProfileDto
import io.github.utshabasak.bookstorebd.core.network.dto.PasswordRequest
import io.github.utshabasak.bookstorebd.core.network.dto.TwoFactorRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in person's profile and account settings. The profile is kept
 * under the account, so it shows at once next time and is removed on sign-out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ProfileRepository
    @Inject
    constructor(
        private val api: AccountApi,
        private val cache: ResponseCache,
        private val mappers: AccountMappers,
        private val json: Json,
        private val session: SessionManager,
        @ApplicationContext private val context: Context,
        @Dispatcher(BookStoreDispatchers.IO) private val io: CoroutineDispatcher,
    ) {
        /** The profile, or null when signed out or not loaded yet. */
        val profile: Flow<OwnProfile?> =
            session.user.flatMapLatest { user ->
                if (user == null) {
                    flowOf(null)
                } else {
                    cache.observe(ResponseCache.userKey(user.id, KEY), OwnProfileDto.serializer()).map { it?.let(mappers::profile) }
                }
            }

        suspend fun refresh(): Outcome<OwnProfile> {
            val user = session.currentUser() ?: return Outcome.Failure(AppError.SignInRequired)
            return apiCall(json) { api.ownProfile() }
                .also { if (it is Outcome.Success) cache.put(ResponseCache.userKey(user.id, KEY), OwnProfileDto.serializer(), it.value) }
                .map(mappers::profile)
        }

        /** Checkout's contact details, from the saved profile when there is one. */
        suspend fun contactDetails(): ContactDetails? {
            val user = session.currentUser() ?: return null
            val dto = cache.get(ResponseCache.userKey(user.id, KEY), OwnProfileDto.serializer()) ?: return null
            return ContactDetails(dto.username, dto.phone.orEmpty(), dto.address.orEmpty())
        }

        /**
         * Saves the edit form. A password change or a change to an existing
         * bKash number needs [ProfileChanges.currentPassword]; without it the
         * server answers 403 with the code `current-password`.
         */
        suspend fun update(changes: ProfileChanges): Outcome<OwnProfile> {
            val saved = apiCall(json) { api.updateProfile(parts(changes)) }
            return when (saved) {
                is Outcome.Failure -> saved
                is Outcome.Success -> refresh()
            }
        }

        private fun parts(changes: ProfileChanges): List<MultipartBody.Part> =
            buildList {
                add(MultipartBody.Part.createFormData("username", changes.username.trim()))
                if (changes.newPassword.isNotEmpty()) add(MultipartBody.Part.createFormData("password", changes.newPassword))
                if (changes.currentPassword.isNotEmpty()) add(MultipartBody.Part.createFormData("currentPassword", changes.currentPassword))
                add(MultipartBody.Part.createFormData("address", changes.address.trim()))
                add(MultipartBody.Part.createFormData("phone", changes.phone.filter(Char::isDigit)))
                add(MultipartBody.Part.createFormData("bkashMerchant", changes.bkashMerchant.trim()))
                add(MultipartBody.Part.createFormData("dateOfBirth", changes.dateOfBirth.trim()))
                add(MultipartBody.Part.createFormData("gender", changes.gender?.apiValue.orEmpty()))
                picture("profilePicture", changes.picture)?.let(::add)
                picture("buyerBanner", changes.buyerBanner)?.let(::add)
                picture("sellerBanner", changes.sellerBanner)?.let(::add)
            }

        private fun picture(
            field: String,
            change: PictureChange,
        ): MultipartBody.Part? =
            when (change) {
                PictureChange.Keep -> {
                    null
                }

                PictureChange.Remove -> {
                    MultipartBody.Part.createFormData(field, "")
                }

                is PictureChange.Replace -> {
                    MultipartBody.Part.createFormData(
                        field,
                        change.image.fileName,
                        change.image.bytes.toRequestBody(change.image.mimeType.toMediaType()),
                    )
                }
            }

        /** Turning two-step sign-in off needs the password; turning it on does not. */
        suspend fun setTwoStep(
            enabled: Boolean,
            password: String? = null,
        ): Outcome<String> =
            apiCall(json) { api.setTwoFactor(TwoFactorRequest(enabled, password?.takeIf { it.isNotEmpty() })) }
                .map { it.message }
                .also { if (it is Outcome.Success) refresh() }

        suspend fun invites(): Outcome<Invites> = apiCall(json) { api.invites() }.map(mappers::invites)

        suspend fun invite(
            emails: List<String>,
            note: String,
        ): Outcome<String> = apiCall(json) { api.invite(InviteRequest(emails, note.trim().takeIf { it.isNotEmpty() })) }.map { it.message }

        /** Saves everything the shop holds about the account, as JSON, to the file the person chose. */
        suspend fun exportData(destination: Uri): Outcome<Unit> =
            when (val body = apiCall(json) { api.exportData() }) {
                is Outcome.Failure -> {
                    body
                }

                is Outcome.Success -> {
                    withContext(io) {
                        try {
                            body.value.use { response ->
                                val out = context.contentResolver.openOutputStream(destination) ?: throw IOException("No output")
                                out.use { response.byteStream().copyTo(it) }
                            }
                            Outcome.Success(Unit)
                        } catch (failed: IOException) {
                            Outcome.Failure(AppError.Unexpected(failed))
                        }
                    }
                }
            }

        /** Deletes the account for good, then ends the session on this phone. */
        suspend fun deleteAccount(password: String): Outcome<String> {
            val result = apiCall(json) { api.deleteAccount(PasswordRequest(password)) }.map { it.message }
            if (result is Outcome.Success) session.endLocally()
            return result
        }

        private companion object {
            const val KEY = "profile"
        }
    }
