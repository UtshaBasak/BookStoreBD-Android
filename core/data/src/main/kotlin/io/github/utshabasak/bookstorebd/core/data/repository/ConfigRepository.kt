package io.github.utshabasak.bookstorebd.core.data.repository

import io.github.utshabasak.bookstorebd.core.data.cache.ResponseCache
import io.github.utshabasak.bookstorebd.core.model.AppConfig
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.map
import io.github.utshabasak.bookstorebd.core.network.BookStoreApi
import io.github.utshabasak.bookstorebd.core.network.Mappers
import io.github.utshabasak.bookstorebd.core.network.apiCall
import io.github.utshabasak.bookstorebd.core.network.dto.AppConfigDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** The server's configuration for the app: versions, sign-in options and the shop's fixed lists. */
@Singleton
class ConfigRepository
    @Inject
    constructor(
        private val api: BookStoreApi,
        private val cache: ResponseCache,
        private val mappers: Mappers,
        private val json: Json,
    ) {
        /** The last configuration received, or null before the first. */
        val config: Flow<AppConfig?> = cache.observe(KEY, AppConfigDto.serializer()).map { it?.let(mappers::config) }

        private val refreshing = Mutex()
        private var fetchedAt = 0L
        private var attempts = 0L
        private var lastResult: Outcome<AppConfig>? = null

        /**
         * Asks the server. Callers at the same moment (the launch, the update
         * check, a sign-in form) share one request and its answer, success or
         * failure, and a fresh answer is reused for a minute.
         */
        suspend fun refresh(): Outcome<AppConfig> {
            val attemptsBefore = attempts
            return refreshing.withLock {
                val shared = lastResult
                if (attempts != attemptsBefore && shared != null) return@withLock shared
                val fresh = System.currentTimeMillis() - fetchedAt < FRESH_MS
                if (fresh) cache.get(KEY, AppConfigDto.serializer())?.let { return@withLock Outcome.Success(mappers.config(it)) }
                val result = apiCall(json) { api.appConfig() }
                if (result is Outcome.Success) {
                    cache.put(KEY, AppConfigDto.serializer(), result.value)
                    fetchedAt = System.currentTimeMillis()
                }
                result.map(mappers::config).also {
                    lastResult = it
                    attempts++
                }
            }
        }

        /** The configuration, from the cache if there is one, otherwise asked for now. */
        suspend fun current(): AppConfig? =
            cache.get(KEY, AppConfigDto.serializer())?.let(mappers::config) ?: (refresh() as? Outcome.Success)?.value

        private companion object {
            const val KEY = "config"
            const val FRESH_MS = 60_000L
        }
    }
