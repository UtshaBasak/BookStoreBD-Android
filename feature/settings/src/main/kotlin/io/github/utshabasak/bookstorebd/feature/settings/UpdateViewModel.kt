package io.github.utshabasak.bookstorebd.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.common.AppBuildInfo
import io.github.utshabasak.bookstorebd.core.data.repository.ApkInstaller
import io.github.utshabasak.bookstorebd.core.data.repository.UpdateRepository
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.AppRelease
import io.github.utshabasak.bookstorebd.core.model.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Where an update stands. */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus

    data object Checking : UpdateStatus

    data object UpToDate : UpdateStatus

    data class Downloading(
        val progress: Float,
    ) : UpdateStatus

    /** Downloaded and checked against its SHA-256; Android's installer takes it from here. */
    data class Ready(
        val apk: File,
    ) : UpdateStatus

    data class Failed(
        val error: AppError,
    ) : UpdateStatus
}

data class UpdateUiState(
    val currentVersion: String = "",
    /** The newest release, when it is newer than this build. */
    val newer: AppRelease? = null,
    /** True when the app must be updated before it can be used. */
    val required: Boolean = false,
    val status: UpdateStatus = UpdateStatus.Idle,
    val canInstall: Boolean = false,
    val releasesUrl: String = "",
)

/**
 * Keeps the GitHub build on the newest release: checks at every launch, and as
 * soon as a newer release is known, downloads it, checks its SHA-256 and hands
 * it to Android's installer, which only accepts an APK signed with the same key.
 */
@HiltViewModel
class UpdateViewModel
    @Inject
    constructor(
        private val updates: UpdateRepository,
        private val installer: ApkInstaller,
        private val build: AppBuildInfo,
    ) : ViewModel() {
        private val status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)

        /** The release already fetched without being asked, so it is fetched once. */
        private var fetchedUnasked: Int? = null

        val uiState: StateFlow<UpdateUiState> =
            combine(updates.newerRelease, updates.updateRequired, status) { newer, required, current ->
                UpdateUiState(
                    currentVersion = build.versionName,
                    newer = newer,
                    required = required,
                    status = current,
                    canInstall = build.selfUpdate && installer.canInstall,
                    releasesUrl = build.releasesUrl,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), UpdateUiState(currentVersion = build.versionName))

        /** At launch and on returning to the app: asks, and starts the download when a newer release is out. */
        fun checkAndFetch() {
            if (!build.selfUpdate) return
            viewModelScope.launch {
                val newer = (updates.check() as? Outcome.Success)?.value ?: return@launch
                if (installer.canInstall && fetchedUnasked != newer.versionCode && status.value.isIdle()) {
                    fetchedUnasked = newer.versionCode
                    download(newer)
                }
            }
        }

        /** "Check for updates" in Account: asks now, and says so when neither source can be reached. */
        fun checkNow() {
            viewModelScope.launch {
                status.value = UpdateStatus.Checking
                status.value =
                    when (val result = updates.check()) {
                        is Outcome.Success -> if (result.value == null) UpdateStatus.UpToDate else UpdateStatus.Idle
                        is Outcome.Failure -> UpdateStatus.Failed(result.error)
                    }
            }
        }

        /** Installs what is already downloaded, or downloads [release] first. */
        fun update(release: AppRelease) {
            if (status.value is UpdateStatus.Ready) install() else download(release)
        }

        fun download(release: AppRelease) {
            if (status.value is UpdateStatus.Downloading) return
            viewModelScope.launch {
                status.value = UpdateStatus.Downloading(0f)
                when (val result = updates.download(release) { progress -> status.update { UpdateStatus.Downloading(progress) } }) {
                    is Outcome.Success -> {
                        status.value = UpdateStatus.Ready(result.value)
                        installer.install(result.value)
                    }

                    is Outcome.Failure -> {
                        status.value = UpdateStatus.Failed(result.error)
                    }
                }
            }
        }

        fun install() {
            val ready = status.value as? UpdateStatus.Ready ?: return
            viewModelScope.launch { installer.install(ready.apk) }
        }

        private fun UpdateStatus.isIdle() = this is UpdateStatus.Idle || this is UpdateStatus.UpToDate || this is UpdateStatus.Failed

        private companion object {
            const val STOP_MS = 5_000L
        }
    }
