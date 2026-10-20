package com.bookzor.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.bookzor.core.database.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Insert
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("SELECT * FROM bookmarks WHERE info_hash = :infoHash ORDER BY position_ms ASC")
    fun observeBookmarks(infoHash: String): Flow<List<BookmarkEntity>>

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)
}
