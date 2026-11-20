package com.anywall.converter.domain.model

/**
 * Perfil o Preset de conversión rápida guardado en Room v3 (`conversion_presets` - v0.3).
 * Incluye perfiles integrados y perfiles creados por el usuario.
 */
data class ConversionPreset(
    val id: Long = 0L,
    val name: String,
    val description: String,
    val category: FormatCategory,
    val targetFormat: SupportedFormat,
    val options: ConversionOptions,
    val isBuiltIn: Boolean = false,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)
