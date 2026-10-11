package io.github.utshabasak.bookstorebd.core.data.inbox

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.utshabasak.bookstorebd.core.data.repository.PageLoadException
import io.github.utshabasak.bookstorebd.core.data.session.SessionManager
import io.github.utshabasak.bookstorebd.core.datastore.SettingsStore
import io.github.utshabasak.bookstorebd.core.model.AppNotification
import io.github.utshabasak.bookstorebd.core.model.NotificationChoice
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.map
import io.github.utshabasak.bookstorebd.core.network.InboxMappers
import io.github.utshabasak.bookstorebd.core.network.NotificationApi
import io.github.utshabasak.bookstorebd.core.network.apiCall
import io.github.utshabasak.bookstorebd.core.network.dto.ChannelChoiceDto
import io.github.utshabasak.bookstorebd.core.network.dto.MarkReadRequest
import io.github.utshabasak.bookstorebd.core.network.dto.NotificationSettingsRequest
import io.github.utshabasak.bookstorebd.core.network.dto.PushRegistrationRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** The notification bell: the list, the unread count, what the person hears about, and this phone's push token. */
@Singleton
class NotificationRepository
    @Inject
    constructor(
        private val api: NotificationApi,
        private val mappers: InboxMappers,
        private val session: SessionManager,
        private val settings: SettingsStore,
        private val json: Json,
    ) {
        private val unreadCount = MutableStateFlow(0)

        /** The number on the bell. Zero while signed out. */
        val unread: StateFlow<Int> = unreadCount.asStateFlow()

        fun notifications(): Flow<PagingData<AppNotification>> =
            Pager(PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = PAGE_SIZE, enablePlaceholders = false)) {
                NotificationPagingSource(api, mappers, json) { unreadCount.value = it }
            }.flow

        suspend fun refreshUnread(): Outcome<Int> {
            if (session.currentUser() == null) {
                unreadCount.value = 0
                return Outcome.Success(0)
            }
            return apiCall(json) { api.notifications(page = 1, pageSize = 1, unreadOnly = 1) }
                .map { it.unread }
                .also { if (it is Outcome.Success) unreadCount.value = it.value }
        }

        /** One notification, as a push names it. */
        suspend fun notification(id: String): Outcome<AppNotification> = apiCall(json) { api.notification(id) }.map(mappers::notification)

        /** Marks these read, or every one when [ids] is null. */
        suspend fun markRead(ids: List<String>? = null): Outcome<Unit> {
            val result = apiCall(json) { api.markRead(MarkReadRequest(ids)) }.map { }
            if (result is Outcome.Success) {
                if (ids == null) unreadCount.value = 0 else refreshUnread()
            }
            return result
        }

        /** Counts one more unread, for a notification that arrived live. */
        fun noteArrived() {
            unreadCount.value += 1
        }

        fun clearLocally() {
            unreadCount.value = 0
        }

        suspend fun choices(): Outcome<List<NotificationChoice>> = apiCall(json) { api.settings() }.map(mappers::choices)

        suspend fun saveChoices(choices: List<NotificationChoice>): Outcome<List<NotificationChoice>> =
            apiCall(json) {
                api.updateSettings(
                    NotificationSettingsRequest(
                        choices.associate {
                            it.category.id to ChannelChoiceDto(inApp = it.inApp, email = it.email)
                        },
                    ),
                )
            }.map(mappers::choices)

        /**
         * Files this phone's push token under the current session, so pushes
         * reach it until it signs out; with no token given, the one filed
         * before, again. False while signed out or without a token.
         */
        suspend fun registerPushToken(token: String? = null): Outcome<Boolean> {
            if (token != null) settings.setPushToken(token)
            val current = token ?: settings.pushToken() ?: return Outcome.Success(false)
            val refreshToken = session.refreshToken() ?: return Outcome.Success(false)
            return apiCall(json) { api.registerPush(PushRegistrationRequest(refreshToken, current)) }.map { it.enabled }
        }

        /** Forgets the token on this phone; the server forgets it when the session ends. */
        suspend fun forgetPushToken() = settings.setPushToken(null)

        private companion object {
            const val PAGE_SIZE = 20
        }
    }

internal class NotificationPagingSource(
    private val api: NotificationApi,
    private val mappers: InboxMappers,
    private val json: Json,
    private val onUnread: (Int) -> Unit,
) : PagingSource<Int, AppNotification>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AppNotification> {
        val page = params.key ?: 1
        return when (val result = apiCall(json) { api.notifications(page, params.loadSize.coerceAtMost(MAX_PAGE)) }) {
            is Outcome.Success -> {
                onUnread(result.value.unread)
                LoadResult.Page(
                    data = result.value.items.map(mappers::notification),
                    prevKey = if (page > 1) page - 1 else null,
                    nextKey = if (page < result.value.pageCount) page + 1 else null,
                )
            }

            is Outcome.Failure -> {
                LoadResult.Error(PageLoadException(result.error))
            }
        }
    }

    override fun getRefreshKey(state: PagingState<Int, AppNotification>): Int? = null

    private companion object {
        const val MAX_PAGE = 50
    }
}
