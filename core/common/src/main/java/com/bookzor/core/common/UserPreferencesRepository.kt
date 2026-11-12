package com.bookzor.core.common

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferencesRepository private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _theme = MutableStateFlow(loadTheme())
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    private val _jumpBackSec = MutableStateFlow(prefs.getInt(KEY_JUMP_BACK, 15))
    val jumpBackSec: StateFlow<Int> = _jumpBackSec.asStateFlow()

    private val _jumpForwardSec = MutableStateFlow(prefs.getInt(KEY_JUMP_FORWARD, 15))
    val jumpForwardSec: StateFlow<Int> = _jumpForwardSec.asStateFlow()

    private val _smartRewind = MutableStateFlow(prefs.getBoolean(KEY_SMART_REWIND, true))
    val smartRewind: StateFlow<Boolean> = _smartRewind.asStateFlow()

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, false))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _domainMirror = MutableStateFlow(
        prefs.getString(KEY_DOMAIN_MIRROR, "https://audiobookbay.lu") ?: "https://audiobookbay.lu"
    )
    val domainMirror: StateFlow<String> = _domainMirror.asStateFlow()

    private fun loadTheme(): AppTheme {
        val name = prefs.getString(KEY_THEME, AppTheme.OLED_BLACK.name)
        return try {
            AppTheme.valueOf(name ?: AppTheme.OLED_BLACK.name)
        } catch (_: Exception) {
            AppTheme.OLED_BLACK
        }
    }

    fun setTheme(theme: AppTheme) {
        _theme.value = theme
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        ThemeManager.setTheme(theme)
    }

    fun setJumpBackSec(seconds: Int) {
        val clamped = seconds.coerceIn(5, 120)
        _jumpBackSec.value = clamped
        prefs.edit().putInt(KEY_JUMP_BACK, clamped).apply()
    }

    fun setJumpForwardSec(seconds: Int) {
        val clamped = seconds.coerceIn(5, 120)
        _jumpForwardSec.value = clamped
        prefs.edit().putInt(KEY_JUMP_FORWARD, clamped).apply()
    }

    fun setSmartRewind(enabled: Boolean) {
        _smartRewind.value = enabled
        prefs.edit().putBoolean(KEY_SMART_REWIND, enabled).apply()
    }

    fun setWifiOnly(enabled: Boolean) {
        _wifiOnly.value = enabled
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    fun setDomainMirror(domain: String) {
        val clean = domain.trimEnd('/')
        _domainMirror.value = clean
        prefs.edit().putString(KEY_DOMAIN_MIRROR, clean).apply()
    }

    companion object {
        private const val PREFS_NAME = "bookzor_preferences"
        private const val KEY_THEME = "app_theme"
        private const val KEY_JUMP_BACK = "jump_back_sec"
        private const val KEY_JUMP_FORWARD = "jump_forward_sec"
        private const val KEY_SMART_REWIND = "smart_rewind"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_DOMAIN_MIRROR = "domain_mirror"

        @Volatile
        private var INSTANCE: UserPreferencesRepository? = null

        fun getInstance(context: Context): UserPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserPreferencesRepository(context.applicationContext).also {
                    INSTANCE = it
                    ThemeManager.setTheme(it.theme.value)
                }
            }
        }
    }
}
