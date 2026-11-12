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

    @Query("SELECT * FROM bookmarks WHERE info_hash = :infoHash ORDER BY track_index ASC, position_ms ASC")
    fun observeBookmarks(infoHash: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE info_hash = :infoHash ORDER BY track_index ASC, position_ms ASC")
    suspend fun getBookmarks(infoHash: String): List<BookmarkEntity>

    @Query("SELECT COUNT(*) FROM bookmarks WHERE info_hash = :infoHash")
    suspend fun getBookmarkCount(infoHash: String): Int

    @Query("UPDATE bookmarks SET note = :note WHERE id = :id")
    suspend fun updateBookmarkNote(id: Long, note: String?)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)
}
