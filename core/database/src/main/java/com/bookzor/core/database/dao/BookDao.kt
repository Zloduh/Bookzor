package com.bookzor.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.bookzor.core.database.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Upsert
    suspend fun insertOrUpdate(book: BookEntity)

    @Query("SELECT * FROM books WHERE info_hash = :infoHash")
    suspend fun getBook(infoHash: String): BookEntity?

    @Query("SELECT * FROM books WHERE info_hash = :infoHash")
    fun observeBook(infoHash: String): Flow<BookEntity?>

    @Query("SELECT * FROM books ORDER BY last_accessed_at DESC")
    fun observeAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY last_accessed_at DESC")
    suspend fun getAllBooks(): List<BookEntity>

    @Query("SELECT * FROM books ORDER BY last_accessed_at DESC LIMIT 1")
    suspend fun getMostRecentBook(): BookEntity?

    @Query("SELECT * FROM books ORDER BY last_accessed_at DESC LIMIT 1")
    fun observeMostRecentBook(): Flow<BookEntity?>

    @Query("UPDATE books SET last_accessed_at = :timestamp WHERE info_hash = :infoHash")
    suspend fun updateLastAccessed(infoHash: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM books WHERE is_in_library = 1 ORDER BY last_accessed_at DESC")
    fun observeLibraryBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE is_to_read = 1 ORDER BY created_at DESC")
    fun observeToReadBooks(): Flow<List<BookEntity>>

    @Query("UPDATE books SET is_in_library = :inLibrary WHERE info_hash = :infoHash")
    suspend fun setInLibrary(infoHash: String, inLibrary: Boolean)

    @Query("UPDATE books SET is_to_read = :toRead WHERE info_hash = :infoHash")
    suspend fun setToRead(infoHash: String, toRead: Boolean)

    @Query("UPDATE books SET is_download_requested = :download WHERE info_hash = :infoHash")
    suspend fun setDownloadRequested(infoHash: String, download: Boolean)

    @Query("UPDATE books SET is_download_requested = 0")
    suspend fun resetAllDownloadFlags()

    @Query("UPDATE books SET total_duration_ms = :totalDurationMs WHERE info_hash = :infoHash")
    suspend fun updateTotalDuration(infoHash: String, totalDurationMs: Long)

    @Query("DELETE FROM books WHERE info_hash = :infoHash")
    suspend fun deleteBook(infoHash: String)
}
