package com.bookzor.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookzor.core.common.Resource
import com.bookzor.core.scraper.AbbScraperService
import com.bookzor.core.scraper.model.BookSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CatalogUiState(
    val query: String = "",
    val activeCategory: String = "All",
    val isLoading: Boolean = false,
    val books: List<BookSearchResult> = emptyList(),
    val errorMessage: String? = null
)

class CatalogViewModel(
    private val scraperService: AbbScraperService = AbbScraperService()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    init {
        // Load initial featured / recent books
        search("dune")
    }

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
    }

    fun search(query: String = _uiState.value.query) {
        val cleanQuery = query.trim().ifEmpty { "audiobook" }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            when (val result = scraperService.search(cleanQuery)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        books = result.data,
                        isLoading = false,
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

    fun selectCategory(category: String) {
        _uiState.value = _uiState.value.copy(activeCategory = category)
        search(if (category == "All") "audiobook" else category)
    }
}
