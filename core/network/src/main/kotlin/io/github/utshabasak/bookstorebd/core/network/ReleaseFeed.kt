package io.github.utshabasak.bookstorebd.core.network

import io.github.utshabasak.bookstorebd.core.model.AppRelease
import io.github.utshabasak.bookstorebd.core.network.di.BaseClient
import io.github.utshabasak.bookstorebd.core.network.dto.AppReleaseDto
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The newest release, read straight from GitHub Releases: every release
 * publishes `update.json`, and `releases/latest/download/update.json` always
 * points at the newest one. It is a plain download, not GitHub's API, so it
 * has no hourly limit, and it works even when the server cannot ask GitHub.
 */
@Singleton
class ReleaseFeed
    @Inject
    constructor(
        @BaseClient private val client: OkHttpClient,
        private val json: Json,
        private val config: NetworkConfig,
    ) {
        /** The newest release, or null when none has a feed yet. Blocking; call it off the main thread. */
        @Throws(IOException::class)
        fun latest(): AppRelease? {
            if (config.releaseFeedUrl.isBlank()) return null
            val request =
                Request
                    .Builder()
                    .url(config.releaseFeedUrl)
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .build()
            return client.newCall(request).execute().use { response ->
                if (response.code == HTTP_NOT_FOUND) return null
                if (!response.isSuccessful) throw IOException("Release feed answered ${response.code}")
                val dto = parse(response.body.string()) ?: throw IOException("Release feed is not readable")
                // The APK is trusted through this SHA-256, which itself came over HTTPS.
                dto.takeIf { SHA256.matches(it.sha256) }?.let {
                    AppRelease(it.versionName, it.versionCode, it.apkUrl, it.apkSize, it.sha256, it.notes, it.pageUrl)
                }
            }
        }

        // SerializationException is an IllegalArgumentException.
        private fun parse(body: String): AppReleaseDto? =
            try {
                json.decodeFromString(AppReleaseDto.serializer(), body)
            } catch (_: IllegalArgumentException) {
                null
            }

        private companion object {
            const val HTTP_NOT_FOUND = 404
            val SHA256 = Regex("^[0-9a-fA-F]{64}$")
        }
    }
