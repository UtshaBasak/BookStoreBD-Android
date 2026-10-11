package io.github.utshabasak.bookstorebd.core.data.cache

import io.github.utshabasak.bookstorebd.core.database.CachedResponse
import io.github.utshabasak.bookstorebd.core.database.CachedResponseDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The server's answers, kept as received so screens can show them offline.
 * Answers that belong to an account are filed under that account's prefix and
 * forgotten when it signs out.
 */
@Singleton
class ResponseCache
    @Inject
    constructor(
        private val dao: CachedResponseDao,
        private val json: Json,
    ) {
        fun <T> observe(
            key: String,
            serializer: KSerializer<T>,
        ): Flow<T?> = dao.observe(key).map { cached -> cached?.let { decode(it.json, serializer) } }

        suspend fun <T> get(
            key: String,
            serializer: KSerializer<T>,
        ): T? = dao.get(key)?.let { decode(it.json, serializer) }

        suspend fun <T> put(
            key: String,
            serializer: KSerializer<T>,
            value: T,
        ) = dao.put(CachedResponse(key, json.encodeToString(serializer, value), System.currentTimeMillis()))

        suspend fun forgetUser(userId: String) = dao.deleteStartingWith(userPrefix(userId))

        // An answer saved by an older version of the app may no longer decode; it is simply not used.
        private fun <T> decode(
            text: String,
            serializer: KSerializer<T>,
        ): T? = runCatching { json.decodeFromString(serializer, text) }.getOrNull()

        companion object {
            fun userPrefix(userId: String) = "user:$userId:"

            fun userKey(
                userId: String,
                name: String,
            ) = "${userPrefix(userId)}$name"
        }
    }
