package io.github.utshabasak.bookstorebd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.CartRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.SettingsRepository
import io.github.utshabasak.bookstorebd.core.data.session.SessionManager
import io.github.utshabasak.bookstorebd.core.data.util.NetworkMonitor
import io.github.utshabasak.bookstorebd.core.model.ThemePreference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the whole app shares: the theme, whether the phone is online, and the cart's badge. */
@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        settings: SettingsRepository,
        network: NetworkMonitor,
        cart: CartRepository,
        private val session: SessionManager,
        private val config: ConfigRepository,
    ) : ViewModel() {
        val theme: StateFlow<ThemePreference?> =
            settings.theme.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        val isOffline: StateFlow<Boolean> =
            network.isOnline.map {
                !it
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), false)

        val cartCount: StateFlow<Int> =
            cart.cart.map { items ->
                items.sumOf { it.quantity }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), 0)

        init {
            viewModelScope.launch {
                // The configuration (sign-in options, the shop's lists, the latest version) and the
                // session, renewed now so the first screen that needs the account need not wait.
                config.refresh()
                session.renew()
            }
        }

        private companion object {
            const val STOP_MS = 5_000L
        }
    }
