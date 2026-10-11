package io.github.utshabasak.bookstorebd.core.data.repository

import io.github.utshabasak.bookstorebd.core.common.BookStoreDispatchers
import io.github.utshabasak.bookstorebd.core.common.Dispatcher
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.ImageUpload
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.network.OrderApi
import io.github.utshabasak.bookstorebd.core.network.apiCall
import io.github.utshabasak.bookstorebd.core.network.di.BaseClient
import io.github.utshabasak.bookstorebd.core.network.dto.HostedImageDto
import io.github.utshabasak.bookstorebd.core.network.dto.UploadSignatureDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Photos ready to attach to a request: hosted addresses, or the files themselves. */
sealed interface UploadedImages {
    fun parts(field: String): List<MultipartBody.Part>

    data class Hosted(
        val urls: List<String>,
        val publicIds: List<String>,
    ) : UploadedImages {
        override fun parts(field: String) =
            urls.map { MultipartBody.Part.createFormData(field, it) } +
                publicIds.map { MultipartBody.Part.createFormData("imagePublicIds", it) }
    }

    class Inline(
        private val images: List<ImageUpload>,
    ) : UploadedImages {
        override fun parts(field: String) =
            images.map { MultipartBody.Part.createFormData(field, it.fileName, it.bytes.toRequestBody(it.mimeType.toMediaType())) }
    }
}

/**
 * Uploads photos straight to the image host with a short-lived signature from
 * the API, as the website does, so large files never pass through the server.
 * The upload goes out on the plain client: the session token is never sent to
 * a third party. When the API has no image host (503), the photos are attached
 * to the request itself instead.
 */
@Singleton
class ImageUploader
    @Inject
    constructor(
        private val api: OrderApi,
        @BaseClient private val client: OkHttpClient,
        private val json: Json,
        @Dispatcher(BookStoreDispatchers.IO) private val io: CoroutineDispatcher,
    ) {
        suspend fun upload(images: List<ImageUpload>): Outcome<UploadedImages> {
            if (images.isEmpty()) return Outcome.Success(UploadedImages.Hosted(emptyList(), emptyList()))
            return when (val signature = apiCall(json) { api.uploadSignature() }) {
                is Outcome.Failure -> {
                    if ((signature.error as? AppError.Server)?.status == HTTP_UNAVAILABLE) {
                        Outcome.Success(UploadedImages.Inline(images))
                    } else {
                        signature
                    }
                }

                is Outcome.Success -> {
                    host(images, signature.value)
                }
            }
        }

        private suspend fun host(
            images: List<ImageUpload>,
            signature: UploadSignatureDto,
        ): Outcome<UploadedImages> =
            withContext(io) {
                try {
                    val hosted =
                        images.map { image ->
                            val form =
                                MultipartBody
                                    .Builder()
                                    .setType(MultipartBody.FORM)
                                    .addFormDataPart("file", image.fileName, image.bytes.toRequestBody(image.mimeType.toMediaType()))
                                    .addFormDataPart("api_key", signature.apiKey)
                                    .addFormDataPart("timestamp", signature.timestamp.toString())
                                    .addFormDataPart("signature", signature.signature)
                                    .addFormDataPart("folder", signature.folder)
                                    .build()
                            client.newCall(Request.Builder().url(signature.uploadUrl).post(form).build()).execute().use { response ->
                                val body = response.body.string()
                                if (!response.isSuccessful) throw IOException("Upload answered ${response.code}")
                                json.decodeFromString(HostedImageDto.serializer(), body)
                            }
                        }
                    Outcome.Success(UploadedImages.Hosted(hosted.map { it.secureUrl }, hosted.map { it.publicId }))
                } catch (failed: IOException) {
                    Outcome.Failure(
                        if (failed.message?.startsWith("Upload answered") ==
                            true
                        ) {
                            AppError.Server(HTTP_BAD_GATEWAY)
                        } else {
                            AppError.Offline
                        },
                    )
                }
            }

        private companion object {
            const val HTTP_UNAVAILABLE = 503
            const val HTTP_BAD_GATEWAY = 502
        }
    }
