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
    /** A newer release, whether or not it was dismissed: for the Account screen. */
    val newer: AppRelease? = null,
    /** A newer release not yet dismissed: for the prompt at launch. */
    val offered: AppRelease? = null,
    val required: Boolean = false,
    val status: UpdateStatus = UpdateStatus.Idle,
    val canInstall: Boolean = false,
    val releasesUrl: String = "",
)

/**
 * The update check for the APK on GitHub Releases: asks at most once a day,
 * offers a newer release once, downloads it, checks its SHA-256, and hands it
 * to Android's installer, which only accepts an APK signed with the same key.
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

        val uiState: StateFlow<UpdateUiState> =
            combine(updates.newerRelease, updates.offeredRelease, updates.updateRequired, status) { newer, offered, required, current ->
                UpdateUiState(
                    currentVersion = build.versionName,
                    newer = newer,
                    offered = offered.takeIf { build.selfUpdate },
                    required = required,
                    status = current,
                    canInstall = build.selfUpdate && installer.canInstall,
                    releasesUrl = build.releasesUrl,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), UpdateUiState(currentVersion = build.versionName))

        /** At launch: asks the server if a day has passed since the last time. */
        fun checkIfDue() {
            if (!build.selfUpdate) return
            viewModelScope.launch { updates.check(force = false) }
        }

        /** "Check for updates" in Account: asks now. */
        fun checkNow() {
            viewModelScope.launch {
                status.value = UpdateStatus.Checking
                status.value =
                    when (val result = updates.check(force = true)) {
                        is Outcome.Success -> if (result.value == null) UpdateStatus.UpToDate else UpdateStatus.Idle
                        is Outcome.Failure -> UpdateStatus.Failed(result.error)
                    }
            }
        }

        fun dismiss(release: AppRelease) {
            viewModelScope.launch { updates.dismiss(release) }
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

        private companion object {
            const val STOP_MS = 5_000L
        }
    }
