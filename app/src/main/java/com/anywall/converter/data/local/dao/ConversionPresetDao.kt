package com.anywall.converter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anywall.converter.data.local.entity.ConversionPresetEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para consultar y gestionar presets/perfiles de conversión rápida (v0.3).
 */
@Dao
interface ConversionPresetDao {

    @Query("SELECT * FROM conversion_presets ORDER BY is_built_in DESC, created_at_millis DESC")
    fun observeAllPresets(): Flow<List<ConversionPresetEntity>>

    @Query("SELECT COUNT(*) FROM conversion_presets")
    suspend fun getPresetCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(entity: ConversionPresetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresets(entities: List<ConversionPresetEntity>)

    @Query("DELETE FROM conversion_presets WHERE id = :id")
    suspend fun deletePresetById(id: Long)
}
