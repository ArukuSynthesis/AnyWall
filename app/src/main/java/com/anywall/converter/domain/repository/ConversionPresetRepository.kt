package com.anywall.converter.domain.repository

import com.anywall.converter.domain.model.ConversionPreset
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para gestionar perfiles de conversión rápida locales en AnyWall (v0.3).
 */
interface ConversionPresetRepository {

    fun observeAllPresets(): Flow<List<ConversionPreset>>

    suspend fun ensureBuiltInPresetsSeeded()

    suspend fun savePreset(preset: ConversionPreset): Long

    suspend fun deletePreset(id: Long)
}
