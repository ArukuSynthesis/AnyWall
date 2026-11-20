package com.anywall.converter.domain.engine

import com.anywall.converter.domain.model.ConversionJobResult
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.SupportedFormat

/**
 * Coordinador central que enruta las solicitudes de conversión al motor apropiado
 * (`ImageEngineImpl`, `DocumentEngineImpl`, `MediaEngineImpl` o `ArchiveEngineImpl`).
 * En v0.2 soporta además lotes mixtos de Imágenes + Documentos/PDF hacia un único PDF continuo.
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

        // Si el lote mezcla Imágenes y Documentos/PDF, permitir unirlos o convertirlos a PDF
        val allImagesOrDocs = distinctSources.all {
            it.category == FormatCategory.IMAGE || it.category == FormatCategory.DOCUMENT
        }
        val hasBothImageAndDoc = distinctSources.any { it.category == FormatCategory.IMAGE } &&
            distinctSources.any { it.category == FormatCategory.DOCUMENT }
        if (allImagesOrDocs && hasBothImageAndDoc) {
            result.add(SupportedFormat.PDF)
        }

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
        // 1. Si el destino es empaquetado (ZIP, TAR, TAR.GZ, TAR.XZ, TAR.BZ2) o extracción -> ArchiveEngine
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

        // 4. Si los orígenes son documentos/PDF (o lote mixto de imágenes + documentos) y el destino es documental -> DocumentEngine
        val allDocsOrImages = sourceFormats.isNotEmpty() &&
            sourceFormats.all { it.category == FormatCategory.DOCUMENT || it.category == FormatCategory.IMAGE }
        if (allDocsOrImages && targetFormat.category == FormatCategory.DOCUMENT) {
            return documentEngine
        }

        // 5. Si los orígenes son audio/video -> MediaEngine
        val allMedia = sourceFormats.isNotEmpty() &&
            sourceFormats.all { it.category == FormatCategory.MEDIA }
        if (allMedia && targetFormat.category == FormatCategory.MEDIA) {
            return mediaEngine
        }

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
