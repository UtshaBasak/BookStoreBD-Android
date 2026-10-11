package io.github.utshabasak.bookstorebd.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.utshabasak.bookstorebd.core.common.BookStoreDispatchers
import io.github.utshabasak.bookstorebd.core.common.Dispatcher
import io.github.utshabasak.bookstorebd.core.datastore.SettingsStore
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.AppRelease
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.network.BaseClientCall
import io.github.utshabasak.bookstorebd.core.network.NetworkConfig
import io.github.utshabasak.bookstorebd.core.network.UpdateGate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    fun install(apk: File)
}

/**
 * The update check, for the APK published on GitHub Releases. The server asks
 * GitHub on the app's behalf (GET /api/v1/app/config), so this only reads the
 * configuration: whether a newer release exists, and whether this build is
 * still supported at all.
 */
@Singleton
class UpdateRepository
    @Inject
    constructor(
        private val configRepository: ConfigRepository,
        private val settings: SettingsStore,
        private val gate: UpdateGate,
        private val download: BaseClientCall,
        config: NetworkConfig,
        @ApplicationContext private val context: Context,
        @Dispatcher(BookStoreDispatchers.IO) private val io: CoroutineDispatcher,
    ) {
        private val currentVersion = config.versionCode

        /** The newest release, when it is newer than this build. */
        val newerRelease: Flow<AppRelease?> =
            configRepository.config.map { it?.latestRelease?.takeIf { release -> release.versionCode > currentVersion } }

        /** The same, unless the person has already said "not now" to it. */
        val offeredRelease: Flow<AppRelease?> =
            combine(newerRelease, settings.dismissedUpdate) { release, dismissed -> release?.takeIf { it.versionCode > dismissed } }

        /** True when the API no longer serves this build. */
        val updateRequired: Flow<Boolean> =
            combine(gate.updateRequired, configRepository.config) { refused, config ->
                refused || (config != null && config.minVersionCode > currentVersion)
            }

        /** Asks the server again, at most once a day unless [force]d. */
        suspend fun check(force: Boolean = false): Outcome<AppRelease?> {
            val now = System.currentTimeMillis()
            if (!force && now - settings.lastUpdateCheck() < DAY_MS) {
                return Outcome.Success(configRepository.current()?.latestRelease?.takeIf { it.versionCode > currentVersion })
            }
            return when (val result = configRepository.refresh()) {
                is Outcome.Success -> {
                    settings.setLastUpdateCheck(now)
                    Outcome.Success(result.value.latestRelease?.takeIf { it.versionCode > currentVersion })
                }

                is Outcome.Failure -> {
                    result
                }
            }
        }

        suspend fun dismiss(release: AppRelease) = settings.dismissUpdate(release.versionCode)

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
            const val DAY_MS = 24L * 60 * 60 * 1000
            const val BUFFER_BYTES = 64 * 1024
        }
    }
