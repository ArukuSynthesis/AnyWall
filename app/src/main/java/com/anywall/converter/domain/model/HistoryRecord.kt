package com.anywall.converter.domain.model

import android.net.Uri

/**
 * Modelo de dominio para un elemento del historial persistido en Room.
 */
data class HistoryRecord(
    val id: Long,
    val sourceDisplayName: String,
    val sourceFormat: SupportedFormat,
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
}
