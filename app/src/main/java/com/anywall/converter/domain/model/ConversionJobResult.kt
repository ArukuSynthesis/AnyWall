package com.anywall.converter.domain.model

import android.net.Uri

/**
 * Resultado individual generado por un motor de conversión.
 */
data class ConversionJobResult(
    val sourceDisplayName: String,
    val sourceFormat: SupportedFormat,
    val outputDisplayName: String,
    val outputFormat: SupportedFormat,
    val outputCategory: FormatCategory,
    val outputUri: Uri,
    val outputAbsolutePath: String,
    val outputSizeBytes: Long,
    val outputMimeType: String,
    val timestampMillis: Long = System.currentTimeMillis()
)
