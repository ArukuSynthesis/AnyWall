package com.anywall.converter.domain.model

import android.net.Uri
import kotlin.math.roundToInt

/**
 * Modelo de dominio para un elemento del historial persistido en Room (v0.2).
 */
data class HistoryRecord(
    val id: Long,
    val sourceDisplayName: String,
    val sourceFormat: SupportedFormat,
    val sourceSizeBytes: Long = 0L,
    val outputDisplayName: String,
    val outputFormat: SupportedFormat,
    val category: FormatCategory,
    val outputUriString: String,
    val outputAbsolutePath: String,
    val outputSizeBytes: Long,
    val outputMimeType: String,
    val createdAtMillis: Long
) {
    val outputUri: Uri
        get() = Uri.parse(outputUriString)

    val formattedSize: String
        get() = InputFileItem.formatBytes(outputSizeBytes)

    val formattedSourceSize: String
        get() = InputFileItem.formatBytes(sourceSizeBytes)

    /**
     * Porcentaje de variación de peso respecto al archivo original.
     * Un valor negativo (ej. -62) indica ahorro de espacio; positivo indica incremento.
     */
    val sizeChangePercent: Int?
        get() {
            if (sourceSizeBytes <= 0L || outputSizeBytes <= 0L) return null
            val ratio = ((outputSizeBytes.toDouble() - sourceSizeBytes.toDouble()) / sourceSizeBytes.toDouble()) * 100.0
            return ratio.roundToInt()
        }

    val sizeComparisonBadgeText: String?
        get() {
            val delta = sizeChangePercent ?: return null
            val sign = if (delta > 0) "+$delta%" else "$delta%"
            return "$formattedSourceSize → $formattedSize ($sign)"
        }
}
