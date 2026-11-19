package com.anywall.converter.domain.engine

import com.anywall.converter.domain.model.ConversionJobResult
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.SupportedFormat

/**
 * Coordinador central que enruta las solicitudes de conversión al motor apropiado
 * (`ImageEngineImpl`, `DocumentEngineImpl`, `MediaEngineImpl` o `ArchiveEngineImpl`).
 */
class ConverterEngineRegistry(
    val imageEngine: FileConverterEngine,
    val documentEngine: FileConverterEngine,
    val mediaEngine: FileConverterEngine,
    val archiveEngine: FileConverterEngine
) {
    private val engines: List<FileConverterEngine> = listOf(
        imageEngine,
        documentEngine,
        mediaEngine,
        archiveEngine
    )

    /**
     * Devuelve la lista combinada de formatos destino válidos para los archivos seleccionados.
     */
    fun getAvailableTargetFormats(sourceFormats: List<SupportedFormat>): List<SupportedFormat> {
        if (sourceFormats.isEmpty()) return emptyList()
        val distinctSources = sourceFormats.distinct()
        val result = linkedSetOf<SupportedFormat>()

        for (engine in engines) {
            result.addAll(engine.getSupportedTargetFormats(distinctSources))
        }

        return result.toList()
    }

    /**
     * Selecciona el motor idóneo según los formatos de origen y el formato de destino.
     */
    fun resolveEngine(
        sourceFormats: List<SupportedFormat>,
        targetFormat: SupportedFormat
    ): FileConverterEngine {
        // 1. Si el destino es empaquetado (ZIP, TAR, TAR.GZ) o extracción de comprimidos -> ArchiveEngine
        if (targetFormat.isArchiveOutput || targetFormat.isExtractionTarget) {
            return archiveEngine
        }

        // 2. Si todos los orígenes son imágenes y el destino es imagen o PDF -> ImageEngine
        val allImages = sourceFormats.isNotEmpty() &&
            sourceFormats.all { it.category == FormatCategory.IMAGE }
        if (allImages && (targetFormat.category == FormatCategory.IMAGE || targetFormat == SupportedFormat.PDF)) {
            return imageEngine
        }

        // 3. Si el origen es PDF y el destino es imagen (JPG / PNG) -> ImageEngine (PdfRenderer)
        val allPdfs = sourceFormats.isNotEmpty() && sourceFormats.all { it == SupportedFormat.PDF }
        if (allPdfs && (targetFormat == SupportedFormat.JPG || targetFormat == SupportedFormat.PNG)) {
            return imageEngine
        }

        // 4. Si los orígenes son documentos/PDF y el destino es PDF, TXT o HTML -> DocumentEngine
        val allDocsOrPdf = sourceFormats.isNotEmpty() &&
            sourceFormats.all { it.category == FormatCategory.DOCUMENT }
        if (allDocsOrPdf && targetFormat.category == FormatCategory.DOCUMENT) {
            return documentEngine
        }

        // 5. Si los orígenes son audio/video -> MediaEngine
        val allMedia = sourceFormats.isNotEmpty() &&
            sourceFormats.all { it.category == FormatCategory.MEDIA }
        if (allMedia && targetFormat.category == FormatCategory.MEDIA) {
            return mediaEngine
        }

        // Fallback: buscar el primer motor que soporte el formato destino para esos orígenes
        return engines.firstOrNull { engine ->
            targetFormat in engine.getSupportedTargetFormats(sourceFormats)
        } ?: engines.firstOrNull { engine ->
            sourceFormats.all { engine.canHandle(it) }
        } ?: archiveEngine
    }

    suspend fun executeConversion(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        require(inputs.isNotEmpty()) { "No se proporcionaron archivos de entrada para convertir." }
        val engine = resolveEngine(inputs.map { it.format }, targetFormat)
        return engine.convert(inputs, targetFormat, options, onProgress)
    }
}
