package io.github.utshabasak.bookstorebd.update

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.utshabasak.bookstorebd.core.data.repository.ApkInstaller
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The Play build: Play delivers updates, and the app never installs packages itself. */
@Singleton
class PlayApkInstaller
    @Inject
    constructor() : ApkInstaller {
        override val canInstall: Boolean = false

        override suspend fun install(apk: File) = Unit
    }

@Module
@InstallIn(SingletonComponent::class)
interface UpdateModule {
    @Binds
    fun bindsApkInstaller(installer: PlayApkInstaller): ApkInstaller
}
