package io.github.utshabasak.bookstorebd.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.utshabasak.bookstorebd.core.network.AccountApi
import io.github.utshabasak.bookstorebd.core.network.AccountMappers
import io.github.utshabasak.bookstorebd.core.network.CommunityApi
import io.github.utshabasak.bookstorebd.core.network.ImageUrls
import io.github.utshabasak.bookstorebd.core.network.NetworkConfig
import io.github.utshabasak.bookstorebd.core.network.OrderApi
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

/** The account, order and community APIs, on the same signed-in client as the shop's. */
@Module
@InstallIn(SingletonComponent::class)
object AccountNetworkModule {
    @Provides
    @Singleton
    fun providesAccountApi(
        client: OkHttpClient,
        config: NetworkConfig,
        json: Json,
    ): AccountApi = NetworkModule.retrofit(client, config, json).create(AccountApi::class.java)

    @Provides
    @Singleton
    fun providesOrderApi(
        client: OkHttpClient,
        config: NetworkConfig,
        json: Json,
    ): OrderApi = NetworkModule.retrofit(client, config, json).create(OrderApi::class.java)

    @Provides
    @Singleton
    fun providesCommunityApi(
        client: OkHttpClient,
        config: NetworkConfig,
        json: Json,
    ): CommunityApi = NetworkModule.retrofit(client, config, json).create(CommunityApi::class.java)

    @Provides
    @Singleton
    fun providesAccountMappers(
        images: ImageUrls,
        config: NetworkConfig,
    ): AccountMappers = AccountMappers(images, config.siteUrl)
}
