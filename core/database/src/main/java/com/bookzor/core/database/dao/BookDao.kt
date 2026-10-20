package com.bookzor.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bookzor.core.database.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(book: BookEntity)

    @Query("SELECT * FROM books WHERE info_hash = :infoHash")
    suspend fun getBook(infoHash: String): BookEntity?

    @Query("SELECT * FROM books WHERE info_hash = :infoHash")
    fun observeBook(infoHash: String): Flow<BookEntity?>

    @Query("SELECT * FROM books ORDER BY last_accessed_at DESC")
    fun observeAllBooks(): Flow<List<BookEntity>>

    @Query("DELETE FROM books WHERE info_hash = :infoHash")
    suspend fun deleteBook(infoHash: String)
}
