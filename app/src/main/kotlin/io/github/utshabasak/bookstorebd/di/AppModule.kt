package io.github.utshabasak.bookstorebd.di

import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.utshabasak.bookstorebd.BuildConfig
import io.github.utshabasak.bookstorebd.core.common.AppBuildInfo
import io.github.utshabasak.bookstorebd.core.network.NetworkConfig
import okhttp3.OkHttpClient
import java.util.Locale
import javax.inject.Qualifier
import javax.inject.Singleton

/** The image loader that sends the session, for pictures only the signed-in person may see. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PrivateImages

/** What only the app module knows: its build, and the phone it runs on. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun providesNetworkConfig(): NetworkConfig =
        NetworkConfig(
            apiBaseUrl = BuildConfig.API_BASE_URL,
            siteUrl = BuildConfig.SITE_URL,
            versionCode = BuildConfig.VERSION_CODE,
            versionName = BuildConfig.VERSION_NAME,
            deviceName = deviceName(),
            androidRelease = Build.VERSION.RELEASE,
            logHttp = BuildConfig.DEBUG,
            releaseFeedUrl = BuildConfig.RELEASE_FEED_URL,
        )

    @Provides
    @Singleton
    fun providesAppBuildInfo(): AppBuildInfo =
        AppBuildInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            selfUpdate = BuildConfig.SELF_UPDATE,
            releasesUrl = BuildConfig.RELEASES_URL,
        )

    /** Chat photos are served only to the two people in the conversation, so they load with the session. */
    @Provides
    @Singleton
    @PrivateImages
    fun providesPrivateImageLoader(
        @ApplicationContext context: Context,
        client: dagger.Lazy<OkHttpClient>,
    ): ImageLoader =
        ImageLoader
            .Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client.get() })) }
            .crossfade(true)
            .build()

    /** "Samsung SM-A155F", or the model alone when it already names its maker ("Pixel 9" stays as it is). */
    private fun deviceName(): String {
        val maker = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.ROOT) }
        val model = Build.MODEL
        return if (model.startsWith(maker, ignoreCase = true) || model.startsWith("Pixel")) model else "$maker $model"
    }
}
