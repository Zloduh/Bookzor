package com.bookzor.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_state",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["info_hash"],
            childColumns = ["info_hash"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaybackStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "info_hash")
    val infoHash: String,
    @ColumnInfo(name = "current_track_index")
    val currentTrackIndex: Int = 0,
    @ColumnInfo(name = "position_ms")
    val positionMs: Long = 0L,
    @ColumnInfo(name = "total_elapsed_ms")
    val totalElapsedMs: Long = 0L,
    @ColumnInfo(name = "playback_speed")
    val playbackSpeed: Float = 1.0f,
    @ColumnInfo(name = "volume_boost_mb")
    val volumeBoostMb: Int = 0,
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
