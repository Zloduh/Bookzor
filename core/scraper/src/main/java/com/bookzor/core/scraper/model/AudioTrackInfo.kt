package com.bookzor.core.scraper.model

data class AudioTrackInfo(
    val index: Int,
    val fileName: String,
    val sizeString: String,
    val sizeBytes: Long
)
