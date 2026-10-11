package io.github.utshabasak.bookstorebd.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.utshabasak.bookstorebd.core.common.ApplicationScope
import io.github.utshabasak.bookstorebd.core.common.BookStoreDispatchers
import io.github.utshabasak.bookstorebd.core.common.Dispatcher
import io.github.utshabasak.bookstorebd.core.datastore.DeviceKeys
import io.github.utshabasak.bookstorebd.core.datastore.KeystoreDeviceKeys
import io.github.utshabasak.bookstorebd.core.datastore.KeystoreSecretCipher
import io.github.utshabasak.bookstorebd.core.datastore.SecretCipher
import io.github.utshabasak.bookstorebd.core.datastore.SessionPreferences
import io.github.utshabasak.bookstorebd.core.datastore.SettingsPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    /** files/datastore/session.preferences_pb: excluded from backups by name. */
    @Provides
    @Singleton
    @SessionPreferences
    fun providesSessionStore(
        @ApplicationContext context: Context,
        @Dispatcher(BookStoreDispatchers.IO) io: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope + io) { context.preferencesDataStoreFile("session") }

    @Provides
    @Singleton
    @SettingsPreferences
    fun providesSettingsStore(
        @ApplicationContext context: Context,
        @Dispatcher(BookStoreDispatchers.IO) io: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope + io) { context.preferencesDataStoreFile("settings") }
}

@Module
@InstallIn(SingletonComponent::class)
interface KeystoreModule {
    @Binds
    fun bindsSecretCipher(cipher: KeystoreSecretCipher): SecretCipher

    @Binds
    fun bindsDeviceKeys(keys: KeystoreDeviceKeys): DeviceKeys
}
