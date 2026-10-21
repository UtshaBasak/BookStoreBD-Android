package io.github.utshabasak.bookstorebd.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.utshabasak.bookstorebd.core.common.BookStoreDispatchers
import io.github.utshabasak.bookstorebd.core.common.Dispatcher
import io.github.utshabasak.bookstorebd.core.data.repository.ApkInstaller
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs a downloaded update through Android's PackageInstaller. The first
 * time, Android asks the person to allow this app to install updates. On
 * Android 12 and newer, once the app has installed itself, later updates go
 * through without another confirmation; before that, and on older versions,
 * Android shows its own. It always refuses an APK not signed with the same
 * key as the installed app.
 */
@Singleton
class PackageInstallerApkInstaller
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @Dispatcher(BookStoreDispatchers.IO) private val io: CoroutineDispatcher,
    ) : ApkInstaller {
        override val canInstall: Boolean = true

        override suspend fun install(apk: File) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                // The one-time permission, on Android's own settings screen for this app.
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return
            }
            withContext(io) {
                val installer = context.packageManager.packageInstaller
                val params =
                    PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                        setAppPackageName(context.packageName)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                        }
                    }
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        apk.inputStream().use { input -> input.copyTo(output) }
                        session.fsync(output)
                    }
                    val callback =
                        PendingIntent.getBroadcast(
                            context,
                            sessionId,
                            Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                        )
                    session.commit(callback.intentSender)
                }
            }
        }
    }

/** Hears back from the installer: shows Android's confirmation when it asks for one. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let { confirm ->
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                // Android replaces the app with the new version; nothing more to do.
            }

            else -> {
                Log.w(TAG, "Update not installed ($status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
            }
        }
    }

    private companion object {
        const val TAG = "BookStoreUpdate"
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface UpdateModule {
    @Binds
    fun bindsApkInstaller(installer: PackageInstallerApkInstaller): ApkInstaller
}
