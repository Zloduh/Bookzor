package com.bookzor.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
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
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "info_hash")
    val infoHash: String,
    @ColumnInfo(name = "track_index")
    val trackIndex: Int,
    @ColumnInfo(name = "position_ms")
    val positionMs: Long,
    val note: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
