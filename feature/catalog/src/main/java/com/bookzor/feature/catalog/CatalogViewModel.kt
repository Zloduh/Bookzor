package com.bookzor.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookzor.core.common.Resource
import com.bookzor.core.scraper.AbbScraperService
import com.bookzor.core.scraper.model.BookSearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CatalogUiState(
    val query: String = "",
    val activeCategory: String = "All",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentPage: Int = 1,
    val canLoadMore: Boolean = true,
    val books: List<BookSearchResult> = emptyList(),
    val errorMessage: String? = null
)

class CatalogViewModel(
    private val scraperService: AbbScraperService = AbbScraperService()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        search("audiobook")
    }

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
    }

    fun search(query: String = _uiState.value.query) {
        val cleanQuery = query.trim().ifEmpty { "audiobook" }
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            query = query,
            isLoading = true,
            currentPage = 1,
            canLoadMore = true,
            errorMessage = null
        )

        searchJob = viewModelScope.launch {
            when (val result = scraperService.search(cleanQuery, page = 1)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        books = result.data,
                        isLoading = false,
                        canLoadMore = result.data.isNotEmpty(),
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

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.canLoadMore) return

        val nextPage = state.currentPage + 1
        val cleanQuery = state.query.trim().ifEmpty { "audiobook" }

        _uiState.value = _uiState.value.copy(isLoadingMore = true)

        viewModelScope.launch {
            when (val result = scraperService.search(cleanQuery, page = nextPage)) {
                is Resource.Success -> {
                    val newBooks = result.data
                    _uiState.value = _uiState.value.copy(
                        books = state.books + newBooks,
                        currentPage = nextPage,
                        canLoadMore = newBooks.isNotEmpty(),
                        isLoadingMore = false
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        canLoadMore = false
                    )
                }
                Resource.Loading -> {}
            }
        }
    }

    fun selectCategory(category: String) {
        _uiState.value = _uiState.value.copy(activeCategory = category)
        search(if (category == "All") "audiobook" else category)
    }
}
