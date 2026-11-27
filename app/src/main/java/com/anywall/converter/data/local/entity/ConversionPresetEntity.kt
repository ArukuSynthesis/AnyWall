package com.anywall.converter.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad Room de `conversion_presets` mantenida por compatibilidad de esquema Room v3.
 */
@Entity(tableName = "conversion_presets")
data class ConversionPresetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "target_format")
    val targetFormatName: String,

    @ColumnInfo(name = "options_serialized")
    val optionsSerialized: String,

    @ColumnInfo(name = "is_built_in")
    val isBuiltIn: Boolean = false,

    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long = System.currentTimeMillis()
)
