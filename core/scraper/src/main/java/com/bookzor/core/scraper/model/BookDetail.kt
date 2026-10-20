package com.bookzor.core.scraper.model

data class BookDetail(
    val title: String,
    val author: String,
    val narrator: String?,
    val coverUrl: String?,
    val format: String, // e.g. "M4B", "MP3"
    val isAbridged: Boolean,
    val durationText: String?,
    val bitrateText: String?,
    val fileSizeText: String,
    val pieceSizeText: String?,
    val description: String,
    val infoHash: String,
    val trackers: List<String>,
    val tracks: List<AudioTrackInfo>,
    val magnetUri: String
)
