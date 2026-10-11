package io.github.utshabasak.bookstorebd.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseTest {
    private lateinit var database: BookStoreDatabase

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BookStoreDatabase::class.java).build()
    }

    @After fun close() = database.close()

    @Test
    fun `keeps the latest answer for each key, and forgets an account's answers by prefix`() =
        runTest {
            val cache = database.cachedResponses()
            cache.put(CachedResponse("home", "{\"v\":1}", 1))
            cache.put(CachedResponse("home", "{\"v\":2}", 2))
            cache.put(CachedResponse("user:u1:cart", "[]", 3))
            cache.put(CachedResponse("user:u1:wishlist", "[]", 3))

            assertThat(cache.get("home")?.json).isEqualTo("{\"v\":2}")

            cache.deleteStartingWith("user:u1:")

            assertThat(cache.get("user:u1:cart")).isNull()
            assertThat(cache.get("user:u1:wishlist")).isNull()
            assertThat(cache.observe("home").first()?.json).isEqualTo("{\"v\":2}")
        }

    @Test
    fun `lists recent searches newest first, once each, and keeps only the newest`() =
        runTest {
            val searches = database.recentSearches()
            searches.put(RecentSearch("harry potter", 1))
            searches.put(RecentSearch("হুমায়ূন", 2))
            searches.put(RecentSearch("harry potter", 3))
            searches.put(RecentSearch("tagore", 4))

            assertThat(searches.observe(10).first()).containsExactly("tagore", "harry potter", "হুমায়ূন").inOrder()

            searches.trim(keep = 2)

            assertThat(searches.observe(10).first()).containsExactly("tagore", "harry potter").inOrder()
        }

    @Test
    fun `keeps recently viewed books in order, capped`() =
        runTest {
            val viewed = database.recentlyViewed()
            (1..5).forEach { viewed.put(RecentlyViewed("b$it", it.toLong())) }
            viewed.put(RecentlyViewed("b2", 10))

            viewed.trim(keep = 3)

            assertThat(viewed.observe(10).first()).containsExactly("b2", "b5", "b4").inOrder()
        }
}
