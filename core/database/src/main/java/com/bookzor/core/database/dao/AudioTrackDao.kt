package com.bookzor.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bookzor.core.database.entity.AudioTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioTrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<AudioTrackEntity>)

    @Query("SELECT * FROM audio_tracks WHERE info_hash = :infoHash ORDER BY track_index ASC")
    suspend fun getTracks(infoHash: String): List<AudioTrackEntity>

    @Query("SELECT * FROM audio_tracks WHERE info_hash = :infoHash ORDER BY track_index ASC")
    fun observeTracks(infoHash: String): Flow<List<AudioTrackEntity>>

    @Query("SELECT * FROM audio_tracks")
    suspend fun getAllTracks(): List<AudioTrackEntity>

    @Query("SELECT * FROM audio_tracks WHERE file_name = :fileName OR file_name LIKE '%' || :fileName LIMIT 1")
    suspend fun findTrackByFileName(fileName: String): AudioTrackEntity?
}
