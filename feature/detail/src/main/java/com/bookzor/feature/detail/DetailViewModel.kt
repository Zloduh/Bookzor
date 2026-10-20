package com.bookzor.feature.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bookzor.core.common.Resource
import com.bookzor.core.database.BookzorDatabase
import com.bookzor.core.database.entity.AudioTrackEntity
import com.bookzor.core.database.entity.BookEntity
import com.bookzor.core.scraper.AbbScraperService
import com.bookzor.core.scraper.model.BookDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val isLoading: Boolean = true,
    val bookDetail: BookDetail? = null,
    val errorMessage: String? = null
)

class DetailViewModel @JvmOverloads constructor(
    application: Application,
    private val scraperService: AbbScraperService = AbbScraperService()
) : AndroidViewModel(application) {

    private val db = BookzorDatabase.getInstance(application)
    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadBook(detailUrl: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            when (val result = scraperService.fetchBookDetail(detailUrl)) {
                is Resource.Success -> {
                    val detail = result.data
                    // Persist to database
                    saveBookToDb(detail, detailUrl)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        bookDetail = detail,
                        errorMessage = null
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                Resource.Loading -> {}
            }
        }
    }

    private suspend fun saveBookToDb(detail: BookDetail, sourceUrl: String) {
        val totalBytes = detail.tracks.sumOf { it.sizeBytes }
        val bookEntity = BookEntity(
            infoHash = detail.infoHash,
            title = detail.title,
            author = detail.author,
            narrator = detail.narrator,
            coverUrl = detail.coverUrl,
            description = detail.description,
            format = detail.format,
            isAbridged = detail.isAbridged,
            totalSizeBytes = totalBytes,
            totalDurationMs = 0L,
            sourceUrl = sourceUrl,
            lastAccessedAt = System.currentTimeMillis()
        )
        db.bookDao().insertOrUpdate(bookEntity)

        val trackEntities = detail.tracks.map { track ->
            AudioTrackEntity(
                id = "${detail.infoHash}_${track.index}",
                infoHash = detail.infoHash,
                trackIndex = track.index,
                fileName = track.fileName,
                fileSizeBytes = track.sizeBytes,
                durationMs = 0L
            )
        }
        db.audioTrackDao().insertTracks(trackEntities)
    }
}
