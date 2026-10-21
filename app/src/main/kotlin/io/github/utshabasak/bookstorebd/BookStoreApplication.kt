package io.github.utshabasak.bookstorebd

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import io.github.utshabasak.bookstorebd.core.network.di.BaseClient
import io.github.utshabasak.bookstorebd.push.InboxSync
import io.github.utshabasak.bookstorebd.push.PushNotifier
import io.github.utshabasak.bookstorebd.push.PushTokens
import io.github.utshabasak.bookstorebd.update.UpdateNotice
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class BookStoreApplication :
    Application(),
    SingletonImageLoader.Factory,
    Configuration.Provider {
    /** The same client as the API's (connection pool, cache, headers), without the session. */
    @Inject @BaseClient
    lateinit var httpClient: dagger.Lazy<OkHttpClient>

    @Inject lateinit var notifier: PushNotifier

    @Inject lateinit var inbox: InboxSync

    @Inject lateinit var pushTokens: PushTokens

    @Inject lateinit var updateNotice: UpdateNotice

    @Inject lateinit var workerFactory: HiltWorkerFactory

    /** WorkManager starts on first use, with workers Hilt can build (the manifest turns off its own start-up). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        inbox.start()
        pushTokens.start()
        updateNotice.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { httpClient.get() })) }
            .crossfade(true)
            .build()
}
