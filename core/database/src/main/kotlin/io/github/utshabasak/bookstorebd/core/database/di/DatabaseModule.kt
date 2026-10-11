package io.github.utshabasak.bookstorebd.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.utshabasak.bookstorebd.core.database.BookStoreDatabase
import io.github.utshabasak.bookstorebd.core.database.CachedResponseDao
import io.github.utshabasak.bookstorebd.core.database.RecentSearchDao
import io.github.utshabasak.bookstorebd.core.database.RecentlyViewedDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * Every table is a cache that can be fetched again, so a schema change
     * with no migration written may start afresh rather than stop the app.
     */
    @Provides
    @Singleton
    fun providesDatabase(
        @ApplicationContext context: Context,
    ): BookStoreDatabase =
        Room
            .databaseBuilder(context, BookStoreDatabase::class.java, "bookstorebd.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun providesCachedResponses(database: BookStoreDatabase): CachedResponseDao = database.cachedResponses()

    @Provides
    fun providesRecentSearches(database: BookStoreDatabase): RecentSearchDao = database.recentSearches()

    @Provides
    fun providesRecentlyViewed(database: BookStoreDatabase): RecentlyViewedDao = database.recentlyViewed()
}
