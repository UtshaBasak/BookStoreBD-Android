package io.github.utshabasak.bookstorebd.di

import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.utshabasak.bookstorebd.BuildConfig
import io.github.utshabasak.bookstorebd.core.common.AppBuildInfo
import io.github.utshabasak.bookstorebd.core.network.NetworkConfig
import java.util.Locale
import javax.inject.Singleton

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

    /** "Samsung SM-A155F", or the model alone when it already names its maker ("Pixel 9" stays as it is). */
    private fun deviceName(): String {
        val maker = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.ROOT) }
        val model = Build.MODEL
        return if (model.startsWith(maker, ignoreCase = true) || model.startsWith("Pixel")) model else "$maker $model"
    }
}
