package com.bookzor.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTheme(val displayName: String) {
    OLED_BLACK("OLED Black"),
    WARM_SEPIA("Warm Sepia"),
    SLATE_NAVY("Slate Navy")
}

object ThemeManager {
    private val _currentTheme = MutableStateFlow(AppTheme.OLED_BLACK)
    val currentTheme: StateFlow<AppTheme> = _currentTheme.asStateFlow()

    fun setTheme(theme: AppTheme) {
        _currentTheme.value = theme
    }
}
