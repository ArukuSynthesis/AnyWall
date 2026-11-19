package com.anywall.converter.domain.engine

import com.anywall.converter.domain.model.ConversionJobResult
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.SupportedFormat

/**
 * Interfaz común obligatoria para los 4 motores de conversión locales de AnyWall.
 */
interface FileConverterEngine {
    fun canHandle(format: SupportedFormat): Boolean

    fun getSupportedTargetFormats(sourceFormats: List<SupportedFormat>): List<SupportedFormat>

    suspend fun convert(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult>
}
