package com.anywall.converter.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Modos de tema visual soportados localmente en AnyWall (v0.3).
 */
enum class AppThemeMode(val labelEs: String, val labelEn: String) {
    SYSTEM("Sistema", "System"),
    LIGHT("Claro", "Light"),
    DARK("Oscuro", "Dark");

    fun localizedLabel(isEnglish: Boolean): String = if (isEnglish) labelEn else labelEs

    companion object {
        fun fromNameOrDefault(name: String?): AppThemeMode {
            if (name.isNullOrBlank()) return SYSTEM
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SYSTEM
        }
    }
}

/**
 * Idioma de la interfaz configurable localmente en AnyWall (v0.3).
 */
enum class AppLanguageMode(val labelEs: String, val labelEn: String) {
    SYSTEM("Sistema", "System"),
    ES("Español", "Spanish"),
    EN("English", "English");

    fun isEnglishActive(): Boolean {
        return when (this) {
            ES -> false
            EN -> true
            SYSTEM -> Locale.getDefault().language.lowercase(Locale.ROOT).startsWith("en")
        }
    }

    companion object {
        fun fromNameOrDefault(name: String?): AppLanguageMode {
            if (name.isNullOrBlank()) return SYSTEM
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SYSTEM
        }
    }
}

/**
 * Estrategia de organización de subcarpetas dentro de `Downloads/AnyWall/` (v0.3).
 */
enum class OutputSubfolderMode(val labelEs: String, val labelEn: String) {
    FLAT("Raíz (Downloads/AnyWall)", "Flat (Downloads/AnyWall)"),
    BY_CATEGORY("Por categoría (Imagenes, PDF...)", "By category (Images, PDF...)"),
    PRESERVE_TREE("Conservar subcarpetas de origen", "Preserve source folder tree"),
    CUSTOM("Subcarpeta personalizada", "Custom subfolder");

    fun localizedLabel(isEnglish: Boolean): String = if (isEnglish) labelEn else labelEs

    companion object {
        fun fromNameOrDefault(name: String?): OutputSubfolderMode {
            if (name.isNullOrBlank()) return FLAT
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: FLAT
        }
    }
}

/**
 * Preferencias locales 100% offline de AnyWall almacenadas en `SharedPreferences` (v0.3).
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        AppThemeMode.fromNameOrDefault(prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name))
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _languageMode = MutableStateFlow(
        AppLanguageMode.fromNameOrDefault(prefs.getString(KEY_LANGUAGE_MODE, AppLanguageMode.ES.name))
    )
    val languageMode: StateFlow<AppLanguageMode> = _languageMode.asStateFlow()

    private val _outputSubfolderMode = MutableStateFlow(
        OutputSubfolderMode.fromNameOrDefault(
            prefs.getString(KEY_OUTPUT_SUBFOLDER_MODE, OutputSubfolderMode.FLAT.name)
        )
    )
    val outputSubfolderMode: StateFlow<OutputSubfolderMode> = _outputSubfolderMode.asStateFlow()

    private val _customSubfolderName = MutableStateFlow(
        prefs.getString(KEY_CUSTOM_SUBFOLDER_NAME, "").orEmpty()
    )
    val customSubfolderName: StateFlow<String> = _customSubfolderName.asStateFlow()

    private val _autoCleanTempCache = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_CLEAN_TEMP_CACHE, true)
    )
    val autoCleanTempCache: StateFlow<Boolean> = _autoCleanTempCache.asStateFlow()

    private val _defaultStripExif = MutableStateFlow(
        prefs.getBoolean(KEY_DEFAULT_STRIP_EXIF, true)
    )
    val defaultStripExif: StateFlow<Boolean> = _defaultStripExif.asStateFlow()

    private val _biometricHistoryLockEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_BIOMETRIC_HISTORY_LOCK, false)
    )
    val biometricHistoryLockEnabled: StateFlow<Boolean> = _biometricHistoryLockEnabled.asStateFlow()

    private val _historyPinCode = MutableStateFlow(
        prefs.getString(KEY_HISTORY_PIN_CODE, "").orEmpty()
    )
    val historyPinCode: StateFlow<String> = _historyPinCode.asStateFlow()

    private val _savedSignaturePngPath = MutableStateFlow(
        prefs.getString(KEY_SAVED_SIGNATURE_PATH, "").orEmpty()
    )
    val savedSignaturePngPath: StateFlow<String> = _savedSignaturePngPath.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setLanguageMode(mode: AppLanguageMode) {
        prefs.edit().putString(KEY_LANGUAGE_MODE, mode.name).apply()
        _languageMode.value = mode
    }

    fun setOutputSubfolderMode(mode: OutputSubfolderMode) {
        prefs.edit().putString(KEY_OUTPUT_SUBFOLDER_MODE, mode.name).apply()
        _outputSubfolderMode.value = mode
    }

    fun setCustomSubfolderName(name: String) {
        val clean = UriFileResolver.sanitizeFileName(name).takeIf { name.isNotBlank() }.orEmpty()
        prefs.edit().putString(KEY_CUSTOM_SUBFOLDER_NAME, clean).apply()
        _customSubfolderName.value = clean
    }

    fun setAutoCleanTempCache(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CLEAN_TEMP_CACHE, enabled).apply()
        _autoCleanTempCache.value = enabled
    }

    fun setDefaultStripExif(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEFAULT_STRIP_EXIF, enabled).apply()
        _defaultStripExif.value = enabled
    }

    fun setBiometricHistoryLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_HISTORY_LOCK, enabled).apply()
        _biometricHistoryLockEnabled.value = enabled
    }

    fun setHistoryPinCode(pin: String) {
        val clean = pin.trim().take(8)
        prefs.edit().putString(KEY_HISTORY_PIN_CODE, clean).apply()
        _historyPinCode.value = clean
    }

    fun setSavedSignaturePngPath(path: String) {
        prefs.edit().putString(KEY_SAVED_SIGNATURE_PATH, path).apply()
        _savedSignaturePngPath.value = path
    }

    companion object {
        private const val PREFS_NAME = "anywall_local_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LANGUAGE_MODE = "language_mode"
        private const val KEY_OUTPUT_SUBFOLDER_MODE = "output_subfolder_mode"
        private const val KEY_CUSTOM_SUBFOLDER_NAME = "custom_subfolder_name"
        private const val KEY_AUTO_CLEAN_TEMP_CACHE = "auto_clean_temp_cache"
        private const val KEY_DEFAULT_STRIP_EXIF = "default_strip_exif"
        private const val KEY_BIOMETRIC_HISTORY_LOCK = "biometric_history_lock"
        private const val KEY_HISTORY_PIN_CODE = "history_pin_code"
        private const val KEY_SAVED_SIGNATURE_PATH = "saved_signature_png_path"
    }
}
