package com.anywall.converter.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.ConversionPreset
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.SupportedFormat

/**
 * Entidad Room para almacenar perfiles/presets de conversión rápida en la base de datos local (v0.3 - Esquema v3).
 */
@Entity(tableName = "conversion_presets")
data class ConversionPresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "category")
    val categoryName: String,

    @ColumnInfo(name = "target_format")
    val targetFormatName: String,

    @ColumnInfo(name = "options_serialized")
    val optionsSerialized: String,

    @ColumnInfo(name = "is_built_in")
    val isBuiltIn: Boolean = false,

    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    fun toDomainModel(): ConversionPreset {
        val outFmt = SupportedFormat.fromNameOrNull(targetFormatName) ?: SupportedFormat.PDF
        val cat = FormatCategory.entries.firstOrNull {
            it.name.equals(categoryName, ignoreCase = true)
        } ?: outFmt.category
        return ConversionPreset(
            id = id,
            name = name,
            description = description,
            category = cat,
            targetFormat = outFmt,
            options = ConversionOptions.deserializeFromString(optionsSerialized),
            isBuiltIn = isBuiltIn,
            createdAtEpochMillis = createdAtMillis
        )
    }

    companion object {
        fun fromDomain(preset: ConversionPreset): ConversionPresetEntity {
            return ConversionPresetEntity(
                id = preset.id,
                name = preset.name,
                description = preset.description,
                categoryName = preset.category.name,
                targetFormatName = preset.targetFormat.name,
                optionsSerialized = preset.options.serializeToString(),
                isBuiltIn = preset.isBuiltIn,
                createdAtMillis = preset.createdAtEpochMillis
            )
        }
    }
}
