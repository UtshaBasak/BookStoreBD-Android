package io.github.utshabasak.bookstorebd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.inbox.NotificationRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CartRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.SettingsRepository
import io.github.utshabasak.bookstorebd.core.data.session.SessionManager
import io.github.utshabasak.bookstorebd.core.data.util.NetworkMonitor
import io.github.utshabasak.bookstorebd.core.model.ThemePreference
import io.github.utshabasak.bookstorebd.core.ui.navigation.ChatRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.MessagesRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.NotificationsRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.routeForSiteLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the whole app shares: the theme, whether the phone is online, the cart's badge, and screens opened from outside the app. */
@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        settings: SettingsRepository,
        network: NetworkMonitor,
        cart: CartRepository,
        private val session: SessionManager,
        private val config: ConfigRepository,
        private val notifications: NotificationRepository,
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

        private val opening = MutableStateFlow<NavKey?>(null)

        /** A screen asked for from outside the app (a notification, a link, a shortcut), until the navigation has shown it. */
        val pendingScreen: StateFlow<NavKey?> = opening.asStateFlow()

        /** A tapped notification: its screen (the list when the app has none for it), and it is read. */
        fun openNotification(
            link: String?,
            notificationId: String?,
        ) {
            opening.value = link?.takeIf { it.isNotBlank() }?.let(::routeForSiteLink) ?: NotificationsRoute
            notificationId?.let { id -> viewModelScope.launch { notifications.markRead(listOf(id)) } }
        }

        fun openChat(
            email: String,
            name: String?,
        ) {
            opening.value = ChatRoute(email, name.orEmpty())
        }

        /** A screen a link to the website or a home-screen shortcut asked for. */
        fun open(route: NavKey) {
            opening.value = route
        }

        fun openMessages() {
            opening.value = MessagesRoute
        }

        fun screenShown() {
            opening.value = null
        }

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
