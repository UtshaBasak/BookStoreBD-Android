package io.github.utshabasak.bookstorebd.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.utshabasak.bookstorebd.core.data.session.SessionManager
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.data.util.ConnectivityNetworkMonitor
import io.github.utshabasak.bookstorebd.core.data.util.NetworkMonitor
import io.github.utshabasak.bookstorebd.core.network.AccessTokenProvider
import io.github.utshabasak.bookstorebd.core.network.SessionRefresher

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {
    @Binds
    fun bindsAccessTokenProvider(session: SessionManager): AccessTokenProvider

    @Binds
    fun bindsSessionRefresher(session: SessionManager): SessionRefresher

    @Binds
    fun bindsUserSession(session: SessionManager): UserSession

    @Binds
    fun bindsNetworkMonitor(monitor: ConnectivityNetworkMonitor): NetworkMonitor
}
