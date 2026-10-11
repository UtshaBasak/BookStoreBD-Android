package io.github.utshabasak.bookstorebd.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The offline copy of what the app has already shown: the server's answers,
 * kept as received, so a screen opened without a connection shows what it
 * showed last time rather than nothing. Everything here can be fetched again,
 * so it is left out of backups.
 */
@Entity(tableName = "cached_responses")
data class CachedResponse(
    /** What was asked for, such as "home" or "book:<id>"; scoped to the account where it is personal. */
    @PrimaryKey val key: String,
    val json: String,
    val savedAt: Long,
)

@Entity(tableName = "recent_searches")
data class RecentSearch(
    @PrimaryKey val term: String,
    val searchedAt: Long,
)

@Entity(tableName = "recently_viewed")
data class RecentlyViewed(
    @PrimaryKey val bookId: String,
    val viewedAt: Long,
)

@Dao
interface CachedResponseDao {
    @Query("SELECT * FROM cached_responses WHERE `key` = :key")
    fun observe(key: String): Flow<CachedResponse?>

    @Query("SELECT * FROM cached_responses WHERE `key` = :key")
    suspend fun get(key: String): CachedResponse?

    @Upsert
    suspend fun put(response: CachedResponse)

    @Query("DELETE FROM cached_responses WHERE `key` = :key")
    suspend fun delete(key: String)

    /** Forgets everything personal on sign-out: keys that start with the account's prefix. */
    @Query("DELETE FROM cached_responses WHERE `key` LIKE :prefix || '%'")
    suspend fun deleteStartingWith(prefix: String)
}

@Dao
interface RecentSearchDao {
    @Query("SELECT term FROM recent_searches ORDER BY searchedAt DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<String>>

    @Upsert
    suspend fun put(search: RecentSearch)

    @Query("DELETE FROM recent_searches WHERE term = :term")
    suspend fun delete(term: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clear()

    /** Keeps the newest [keep]. */
    @Query("DELETE FROM recent_searches WHERE term NOT IN (SELECT term FROM recent_searches ORDER BY searchedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Dao
interface RecentlyViewedDao {
    @Query("SELECT bookId FROM recently_viewed ORDER BY viewedAt DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<String>>

    @Upsert
    suspend fun put(viewed: RecentlyViewed)

    @Query("DELETE FROM recently_viewed WHERE bookId NOT IN (SELECT bookId FROM recently_viewed ORDER BY viewedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("DELETE FROM recently_viewed")
    suspend fun clear()
}

@Database(
    entities = [CachedResponse::class, RecentSearch::class, RecentlyViewed::class],
    version = 1,
    exportSchema = true,
)
abstract class BookStoreDatabase : RoomDatabase() {
    abstract fun cachedResponses(): CachedResponseDao

    abstract fun recentSearches(): RecentSearchDao

    abstract fun recentlyViewed(): RecentlyViewedDao
}
