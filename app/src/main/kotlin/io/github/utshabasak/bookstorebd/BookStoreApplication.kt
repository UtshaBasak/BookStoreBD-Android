package io.github.utshabasak.bookstorebd

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import io.github.utshabasak.bookstorebd.core.network.di.BaseClient
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class BookStoreApplication :
    Application(),
    SingletonImageLoader.Factory {
    /** The same client as the API's (connection pool, cache, headers), without the session. */
    @Inject @BaseClient
    lateinit var httpClient: dagger.Lazy<OkHttpClient>

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { httpClient.get() })) }
            .crossfade(true)
            .build()
}
