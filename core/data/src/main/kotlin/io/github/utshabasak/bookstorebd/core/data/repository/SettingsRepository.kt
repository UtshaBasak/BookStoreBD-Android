package io.github.utshabasak.bookstorebd.core.data.repository

import io.github.utshabasak.bookstorebd.core.datastore.SettingsStore
import io.github.utshabasak.bookstorebd.core.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** The person's own settings. The language is kept by Android (per-app language), not here. */
@Singleton
class SettingsRepository
    @Inject
    constructor(
        private val store: SettingsStore,
    ) {
        val theme: Flow<ThemePreference> = store.theme

        suspend fun setTheme(theme: ThemePreference) = store.setTheme(theme)
    }
