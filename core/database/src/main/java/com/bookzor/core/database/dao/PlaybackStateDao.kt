package com.bookzor.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bookzor.core.database.entity.PlaybackStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaybackState(state: PlaybackStateEntity)

    @Query("SELECT * FROM playback_state WHERE info_hash = :infoHash")
    suspend fun getPlaybackState(infoHash: String): PlaybackStateEntity?

    @Query("SELECT * FROM playback_state WHERE info_hash = :infoHash")
    fun observePlaybackState(infoHash: String): Flow<PlaybackStateEntity?>

    @Query("UPDATE playback_state SET position_ms = :positionMs, updated_at = :updatedAt WHERE info_hash = :infoHash")
    suspend fun updatePosition(infoHash: String, positionMs: Long, updatedAt: Long = System.currentTimeMillis())
}
