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

        val rememberSearches: Flow<Boolean> = store.rememberSearches

        val rememberViewed: Flow<Boolean> = store.rememberViewed

        suspend fun setRememberSearches(remember: Boolean) = store.setRememberSearches(remember)

        suspend fun setRememberViewed(remember: Boolean) = store.setRememberViewed(remember)

        val wishlistSort: Flow<String?> = store.wishlistSort

        suspend fun setWishlistSort(name: String) = store.setWishlistSort(name)

        val profileMode: Flow<String?> = store.profileMode

        suspend fun setProfileMode(name: String) = store.setProfileMode(name)

        val voiceLanguage: Flow<String?> = store.voiceLanguage

        suspend fun setVoiceLanguage(tag: String) = store.setVoiceLanguage(tag)
    }
