package io.github.utshabasak.bookstorebd.core.network.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.utshabasak.bookstorebd.core.network.AccessTokenInterceptor
import io.github.utshabasak.bookstorebd.core.network.AccessTokenProvider
import io.github.utshabasak.bookstorebd.core.network.BookStoreApi
import io.github.utshabasak.bookstorebd.core.network.ClientHeadersInterceptor
import io.github.utshabasak.bookstorebd.core.network.ImageUrls
import io.github.utshabasak.bookstorebd.core.network.Mappers
import io.github.utshabasak.bookstorebd.core.network.NetworkConfig
import io.github.utshabasak.bookstorebd.core.network.SessionApi
import io.github.utshabasak.bookstorebd.core.network.SessionRefresher
import io.github.utshabasak.bookstorebd.core.network.TokenAuthenticator
import io.github.utshabasak.bookstorebd.core.network.UpdateGate
import io.github.utshabasak.bookstorebd.core.network.UpdateRequiredInterceptor
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** The client without the session: pictures, the APK download, and the session endpoints themselves. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun providesJson(): Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
            encodeDefaults = true
        }

    /**
     * Timeouts allow for Render's free plan, which sleeps when idle and takes
     * up to a minute to answer the first request after.
     */
    @Provides
    @Singleton
    @BaseClient
    fun providesBaseClient(
        config: NetworkConfig,
        gate: UpdateGate,
        @ApplicationContext context: Context,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .cache(Cache(File(context.cacheDir, "http"), HTTP_CACHE_BYTES))
            .addInterceptor(ClientHeadersInterceptor(config))
            .addInterceptor(UpdateRequiredInterceptor(gate))
            .apply {
                if (config.logHttp) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactHeader("Authorization")
                        },
                    )
                }
            }.build()

    @Provides
    @Singleton
    fun providesApiClient(
        @BaseClient base: OkHttpClient,
        tokens: AccessTokenProvider,
        refresher: SessionRefresher,
    ): OkHttpClient =
        base
            .newBuilder()
            .addInterceptor(AccessTokenInterceptor(tokens))
            .authenticator(TokenAuthenticator(refresher))
            .build()

    @Provides
    @Singleton
    fun providesBookStoreApi(
        client: OkHttpClient,
        config: NetworkConfig,
        json: Json,
    ): BookStoreApi = retrofit(client, config, json).create(BookStoreApi::class.java)

    @Provides
    @Singleton
    fun providesSessionApi(
        @BaseClient client: OkHttpClient,
        config: NetworkConfig,
        json: Json,
    ): SessionApi = retrofit(client, config, json).create(SessionApi::class.java)

    @Provides
    @Singleton
    fun providesImageUrls(config: NetworkConfig): ImageUrls = ImageUrls(config.siteUrl)

    @Provides
    @Singleton
    fun providesMappers(images: ImageUrls): Mappers = Mappers(images)

    private fun retrofit(
        client: OkHttpClient,
        config: NetworkConfig,
        json: Json,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(config.apiBaseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    private const val CONNECT_TIMEOUT_S = 20L
    private const val READ_TIMEOUT_S = 60L
    private const val CALL_TIMEOUT_S = 90L
    private const val HTTP_CACHE_BYTES = 50L * 1024 * 1024
}
