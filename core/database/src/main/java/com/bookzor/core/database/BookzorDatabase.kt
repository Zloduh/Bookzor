package com.bookzor.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bookzor.core.database.dao.AudioTrackDao
import com.bookzor.core.database.dao.BookDao
import com.bookzor.core.database.dao.BookmarkDao
import com.bookzor.core.database.dao.PlaybackStateDao
import com.bookzor.core.database.entity.AudioTrackEntity
import com.bookzor.core.database.entity.BookEntity
import com.bookzor.core.database.entity.BookmarkEntity
import com.bookzor.core.database.entity.PlaybackStateEntity

@Database(
    entities = [
        BookEntity::class,
        AudioTrackEntity::class,
        PlaybackStateEntity::class,
        BookmarkEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BookzorDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun audioTrackDao(): AudioTrackDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        @Volatile
        private var INSTANCE: BookzorDatabase? = null

        fun getInstance(context: Context): BookzorDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BookzorDatabase::class.java,
                    "bookzor_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
