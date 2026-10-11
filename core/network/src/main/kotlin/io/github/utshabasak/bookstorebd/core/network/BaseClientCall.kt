package io.github.utshabasak.bookstorebd.core.network

import io.github.utshabasak.bookstorebd.core.network.di.BaseClient
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** A plain download over the base client, for the update's APK. Blocking; call it off the main thread. */
@Singleton
class BaseClientCall
    @Inject
    constructor(
        @BaseClient private val client: OkHttpClient,
    ) {
        /** Opens [url] and hands the body and its length (or -1) to [read]. */
        fun stream(
            url: String,
            read: (InputStream, Long) -> Unit,
        ) {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed with ${response.code}")
                read(response.body.byteStream(), response.body.contentLength())
            }
        }
    }
