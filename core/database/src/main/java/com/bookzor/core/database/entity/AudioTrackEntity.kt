package com.bookzor.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_tracks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["info_hash"],
            childColumns = ["info_hash"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["info_hash"])]
)
data class AudioTrackEntity(
    @PrimaryKey
    val id: String, // infoHash + "_" + trackIndex
    @ColumnInfo(name = "info_hash")
    val infoHash: String,
    @ColumnInfo(name = "track_index")
    val trackIndex: Int,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long
)
