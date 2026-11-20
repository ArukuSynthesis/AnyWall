package com.anywall.converter.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Modos de tema visual soportados localmente en AnyWall v0.2.
 */
enum class AppThemeMode(val labelEs: String) {
    SYSTEM("Sistema"),
    LIGHT("Claro"),
    DARK("Oscuro");

    companion object {
        fun fromNameOrDefault(name: String?): AppThemeMode {
            if (name.isNullOrBlank()) return SYSTEM
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SYSTEM
        }
    }
}

/**
 * Preferencias locales 100% offline de AnyWall almacenadas en `SharedPreferences`.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        AppThemeMode.fromNameOrDefault(prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name))
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _autoCleanTempCache = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_CLEAN_TEMP_CACHE, true)
    )
    val autoCleanTempCache: StateFlow<Boolean> = _autoCleanTempCache.asStateFlow()

    private val _defaultStripExif = MutableStateFlow(
        prefs.getBoolean(KEY_DEFAULT_STRIP_EXIF, true)
    )
    val defaultStripExif: StateFlow<Boolean> = _defaultStripExif.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setAutoCleanTempCache(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CLEAN_TEMP_CACHE, enabled).apply()
        _autoCleanTempCache.value = enabled
    }

    fun setDefaultStripExif(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEFAULT_STRIP_EXIF, enabled).apply()
        _defaultStripExif.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "anywall_local_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AUTO_CLEAN_TEMP_CACHE = "auto_clean_temp_cache"
        private const val KEY_DEFAULT_STRIP_EXIF = "default_strip_exif"
    }
}
