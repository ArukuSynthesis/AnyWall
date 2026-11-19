package com.anywall.converter.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.anywall.converter.domain.model.ConversionJobResult
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.HistoryRecord
import com.anywall.converter.domain.model.SupportedFormat

/**
 * Entidad Room para registrar cada archivo convertido en la base de datos local.
 */
@Entity(tableName = "conversion_history")
data class ConversionHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "source_display_name")
    val sourceDisplayName: String,

    @ColumnInfo(name = "source_format")
    val sourceFormatName: String,

    @ColumnInfo(name = "output_display_name")
    val outputDisplayName: String,

    @ColumnInfo(name = "output_format")
    val outputFormatName: String,

    @ColumnInfo(name = "category")
    val categoryName: String,

    @ColumnInfo(name = "output_uri")
    val outputUriString: String,

    @ColumnInfo(name = "output_absolute_path")
    val outputAbsolutePath: String,

    @ColumnInfo(name = "output_size_bytes")
    val outputSizeBytes: Long,

    @ColumnInfo(name = "output_mime_type")
    val outputMimeType: String,

    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long
) {
    fun toDomainModel(): HistoryRecord {
        val srcFmt = SupportedFormat.fromNameOrNull(sourceFormatName) ?: SupportedFormat.TXT
        val outFmt = SupportedFormat.fromNameOrNull(outputFormatName) ?: SupportedFormat.TXT
        val cat = FormatCategory.entries.firstOrNull {
            it.name.equals(categoryName, ignoreCase = true)
        } ?: outFmt.category

        return HistoryRecord(
            id = id,
            sourceDisplayName = sourceDisplayName,
            sourceFormat = srcFmt,
            outputDisplayName = outputDisplayName,
            outputFormat = outFmt,
            category = cat,
            outputUriString = outputUriString,
            outputAbsolutePath = outputAbsolutePath,
            outputSizeBytes = outputSizeBytes,
            outputMimeType = outputMimeType,
            createdAtMillis = createdAtMillis
        )
    }

    companion object {
        fun fromJobResult(result: ConversionJobResult): ConversionHistoryEntity {
            return ConversionHistoryEntity(
                sourceDisplayName = result.sourceDisplayName,
                sourceFormatName = result.sourceFormat.name,
                outputDisplayName = result.outputDisplayName,
                outputFormatName = result.outputFormat.name,
                categoryName = result.outputCategory.name,
                outputUriString = result.outputUri.toString(),
                outputAbsolutePath = result.outputAbsolutePath,
                outputSizeBytes = result.outputSizeBytes,
                outputMimeType = result.outputMimeType,
                createdAtMillis = result.timestampMillis
            )
        }
    }
}
