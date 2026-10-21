package io.github.utshabasak.bookstorebd.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.utshabasak.bookstorebd.core.common.AppBuildInfo
import io.github.utshabasak.bookstorebd.core.common.BookStoreDispatchers
import io.github.utshabasak.bookstorebd.core.common.Dispatcher
import io.github.utshabasak.bookstorebd.core.datastore.SettingsStore
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.AppRelease
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.network.BaseClientCall
import io.github.utshabasak.bookstorebd.core.network.NetworkConfig
import io.github.utshabasak.bookstorebd.core.network.ReleaseFeed
import io.github.utshabasak.bookstorebd.core.network.UpdateGate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Hands a downloaded, verified APK to Android's installer. Only the GitHub build can. */
interface ApkInstaller {
    val canInstall: Boolean

    /** Starts installing; Android asks the person to confirm. */
    suspend fun install(apk: File)
}

/**
 * The update check, for the APK published on GitHub Releases. Two sources are
 * asked together and the newer answer wins: the server's configuration
 * (GET /api/v1/app/config, which asks GitHub on the app's behalf) and the
 * release feed on GitHub itself ([ReleaseFeed]), so one being unavailable
 * never hides an update.
 *
 * The GitHub build is always kept on the newest release: once a newer one is
 * known, the app asks to be updated before anything else.
 */
@Singleton
class UpdateRepository
    @Inject
    constructor(
        private val configRepository: ConfigRepository,
        private val settings: SettingsStore,
        private val gate: UpdateGate,
        private val download: BaseClientCall,
        private val feed: ReleaseFeed,
        private val build: AppBuildInfo,
        config: NetworkConfig,
        @ApplicationContext private val context: Context,
        @Dispatcher(BookStoreDispatchers.IO) private val io: CoroutineDispatcher,
    ) {
        private val currentVersion = config.versionCode
        private val feedRelease = MutableStateFlow<AppRelease?>(null)

        /** The newest release either source knows of, when it is newer than this build. */
        val newerRelease: Flow<AppRelease?> =
            combine(configRepository.config, feedRelease) { config, fromFeed ->
                listOfNotNull(config?.latestRelease, fromFeed)
                    .maxByOrNull { it.versionCode }
                    ?.takeIf { it.versionCode > currentVersion }
            }.distinctUntilChanged()

        /**
         * True when this build may no longer be used: the API refuses it, or
         * (in the GitHub build) a newer release is out, even if this phone
         * only learnt of it before going offline.
         */
        val updateRequired: Flow<Boolean> =
            combine(gate.updateRequired, configRepository.config, settings.newestVersionSeen, newerRelease) {
                refused,
                config,
                seen,
                newer,
                ->
                refused ||
                    (config != null && config.minVersionCode > currentVersion) ||
                    (build.selfUpdate && (newer != null || seen > currentVersion))
            }.distinctUntilChanged()

        /**
         * Asks both sources now. Succeeds when either answers; fails only when
         * neither can be reached, so "you have the latest version" is never
         * said without an answer behind it.
         */
        suspend fun check(): Outcome<AppRelease?> =
            coroutineScope {
                val server = async { configRepository.refresh() }
                val fromFeed = async(io) { readFeed() }
                val serverResult = server.await()
                val feedResult = fromFeed.await()
                if (feedResult is Outcome.Success) feedRelease.value = feedResult.value
                if (serverResult is Outcome.Failure && feedResult is Outcome.Failure) {
                    return@coroutineScope serverResult
                }
                val newer = newerRelease.first()
                newer?.let { settings.sawVersion(it.versionCode) }
                Outcome.Success(newer)
            }

        private fun readFeed(): Outcome<AppRelease?> =
            try {
                Outcome.Success(feed.latest())
            } catch (_: IOException) {
                Outcome.Failure(AppError.Offline)
            }

        /**
         * Downloads the APK and checks it against the SHA-256 the release
         * published. Android then checks its signature against the installed
         * app's, so only an APK signed with the same key can replace it.
         */
        suspend fun download(
            release: AppRelease,
            onProgress: (Float) -> Unit,
        ): Outcome<File> =
            withContext(io) {
                val folder = File(context.cacheDir, "updates").apply { mkdirs() }
                folder.listFiles()?.forEach { it.delete() }
                val target = File(folder, "BookStoreBD-${release.versionName}.apk")
                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    download.stream(release.apkUrl) { input, length ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(BUFFER_BYTES)
                            var copied = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                copied += read
                                val total = if (length > 0) length else release.apkSize
                                if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                    val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                    if (sha256.equals(release.sha256, ignoreCase = true)) {
                        Outcome.Success(target)
                    } else {
                        target.delete()
                        Outcome.Failure(AppError.Rejected(status = 0, message = null, code = "checksum-mismatch"))
                    }
                } catch (_: IOException) {
                    target.delete()
                    Outcome.Failure(AppError.Offline)
                }
            }

        private companion object {
            const val BUFFER_BYTES = 64 * 1024
        }
    }
