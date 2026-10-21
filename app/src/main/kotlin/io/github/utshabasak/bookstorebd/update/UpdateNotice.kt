package io.github.utshabasak.bookstorebd.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.utshabasak.bookstorebd.BuildConfig
import io.github.utshabasak.bookstorebd.MainActivity
import io.github.utshabasak.bookstorebd.R
import io.github.utshabasak.bookstorebd.core.common.ApplicationScope
import io.github.utshabasak.bookstorebd.core.data.repository.UpdateRepository
import io.github.utshabasak.bookstorebd.core.model.AppRelease
import io.github.utshabasak.bookstorebd.core.model.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The GitHub build's standing notice that a newer version is out. It stays in
 * the notification shade (it cannot be cleared by "Clear all") until the app
 * is updated, and a check in the background every few hours puts it there
 * even when the app is not opened.
 */
@Singleton
class UpdateNotice
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val updates: UpdateRepository,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val manager = NotificationManagerCompat.from(context)

        /** Creates the channel, follows what the app learns while open, and schedules the background check. */
        fun start() {
            if (!BuildConfig.SELF_UPDATE) return
            val channel =
                NotificationChannel(CHANNEL, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_updates_body)
                }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
            updates.newerRelease.onEach(::follow).launchIn(scope)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UpdateCheckWorker.NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UpdateCheckWorker>(CHECK_EVERY_HOURS, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }

        fun follow(release: AppRelease?) {
            if (release == null) manager.cancel(ID) else show(release)
        }

        private fun show(release: AppRelease) {
            // Android 13 and newer ask first; the app itself still asks to be updated when opened.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val open =
                PendingIntent.getActivity(
                    context,
                    ID,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val builder =
                NotificationCompat
                    .Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_notification)
                    .setColor(ContextCompat.getColor(context, R.color.splash_icon_background))
                    .setContentTitle(context.getString(R.string.update_notice_title, release.versionName))
                    .setContentText(context.getString(R.string.update_notice_body))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.update_notice_body)))
                    .setContentIntent(open)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setOnlyAlertOnce(true)
                    .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            runCatching { manager.notify(ID, builder.build()) }
        }

        private companion object {
            const val CHANNEL = "updates"
            const val ID = 0x0B00C
            const val CHECK_EVERY_HOURS = 6L
        }
    }

/** The background check: asks for a newer release and keeps the notice in step. */
@HiltWorker
class UpdateCheckWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val updates: UpdateRepository,
        private val notice: UpdateNotice,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            when (val result = updates.check()) {
                is Outcome.Success -> {
                    notice.follow(result.value)
                    Result.success()
                }

                is Outcome.Failure -> {
                    Result.retry()
                }
            }

        companion object {
            const val NAME = "update-check"
        }
    }
