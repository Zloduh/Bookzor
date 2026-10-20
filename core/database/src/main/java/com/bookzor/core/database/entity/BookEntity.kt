package com.bookzor.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    @ColumnInfo(name = "info_hash")
    val infoHash: String,
    val title: String,
    val author: String,
    val narrator: String?,
    @ColumnInfo(name = "cover_url")
    val coverUrl: String?,
    val description: String?,
    val format: String,
    @ColumnInfo(name = "is_abridged")
    val isAbridged: Boolean,
    @ColumnInfo(name = "total_size_bytes")
    val totalSizeBytes: Long,
    @ColumnInfo(name = "total_duration_ms")
    val totalDurationMs: Long,
    @ColumnInfo(name = "source_url")
    val sourceUrl: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_accessed_at")
    val lastAccessedAt: Long = System.currentTimeMillis()
)
