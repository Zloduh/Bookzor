package com.bookzor.core.scraper.model

data class BookSearchResult(
    val title: String,
    val detailUrl: String,
    val coverUrl: String?,
    val categories: List<String>,
    val language: String? = null
)
