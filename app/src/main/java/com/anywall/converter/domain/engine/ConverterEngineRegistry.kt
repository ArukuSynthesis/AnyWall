package com.anywall.converter.domain.engine

import com.anywall.converter.domain.model.ConversionJobResult
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.SupportedFormat

/**
 * Coordinador central que enruta las solicitudes de conversión al motor apropiado (v0.3):
 * 1. `ImageEngineImpl`
 * 2. `DocumentEngineImpl`
 * 3. `MediaEngineImpl`
 * 4. `ArchiveEngineImpl`
 * 5. `DataAndSecurityEngineImpl`
 */
class ConverterEngineRegistry(
    val imageEngine: FileConverterEngine,
    val documentEngine: FileConverterEngine,
    val mediaEngine: FileConverterEngine,
    val archiveEngine: FileConverterEngine,
    val dataAndSecurityEngine: FileConverterEngine? = null
) {
    private val engines: List<FileConverterEngine> = listOfNotNull(
        imageEngine,
        documentEngine,
        mediaEngine,
        dataAndSecurityEngine,
        archiveEngine
    )

    /**
     * Devuelve la lista combinada de formatos destino válidos para los archivos seleccionados.
     */
    fun getAvailableTargetFormats(sourceFormats: List<SupportedFormat>): List<SupportedFormat> {
        if (sourceFormats.isEmpty()) return emptyList()
        val distinctSources = sourceFormats.distinct()
        val result = linkedSetOf<SupportedFormat>()

        // Si todos son bóvedas AES-256 (.anywall) -> única acción principal es descifrar bóveda
        if (distinctSources.all { it == SupportedFormat.ANYWALL_VAULT }) {
            result.add(SupportedFormat.ANYWALL_VAULT)
            return result.toList()
        }

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

        // Permitir siempre cifrar cualquier archivo en Bóveda AES-256 (.anywall)
        if (dataAndSecurityEngine != null) {
            result.add(SupportedFormat.ANYWALL_VAULT)
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
        // 1. Si el origen o destino es Bóveda AES-256 (.anywall) o código QR (QR_PNG / QR_SVG) o BASE64 -> DataAndSecurityEngine
        if (dataAndSecurityEngine != null) {
            val allVaultSources = sourceFormats.isNotEmpty() &&
                sourceFormats.all { it == SupportedFormat.ANYWALL_VAULT }
            if (allVaultSources ||
                targetFormat == SupportedFormat.ANYWALL_VAULT ||
                targetFormat == SupportedFormat.QR_PNG ||
                targetFormat == SupportedFormat.QR_SVG ||
                targetFormat == SupportedFormat.BASE64
            ) {
                return dataAndSecurityEngine
            }

            // Si todos los orígenes son datos estructurados (CSV, JSON, XML, YAML, BASE64) -> DataAndSecurityEngine
            val allDataSources = sourceFormats.isNotEmpty() &&
                sourceFormats.all { it.category == FormatCategory.DATA_TOOLS }
            if (allDataSources && !targetFormat.isArchiveOutput && targetFormat != SupportedFormat.FILE_PARTS) {
                return dataAndSecurityEngine
            }
        }

        // 2. Si el destino es empaquetado (ZIP, TAR, TAR.GZ, TAR.XZ, TAR.BZ2), extracción o particionado (.part001 / JOIN_PARTS) -> ArchiveEngine
        if (targetFormat.isArchiveOutput ||
            targetFormat.isExtractionTarget ||
            targetFormat == SupportedFormat.FILE_PARTS ||
            targetFormat == SupportedFormat.JOIN_PARTS
        ) {
            return archiveEngine
        }

        // 3. Si todos los orígenes son imágenes y el destino es imagen (incl. ICON_PACK_ZIP, ICO, GIF) o PDF -> ImageEngine
        val allImages = sourceFormats.isNotEmpty() &&
            sourceFormats.all { it.category == FormatCategory.IMAGE }
        if (allImages && (targetFormat.category == FormatCategory.IMAGE || targetFormat == SupportedFormat.PDF)) {
            return imageEngine
        }

        // 4. Si el origen es PDF y el destino es imagen (JPG / PNG / WEBP) -> ImageEngine (PdfRenderer)
        val allPdfs = sourceFormats.isNotEmpty() && sourceFormats.all { it == SupportedFormat.PDF }
        if (allPdfs && (targetFormat == SupportedFormat.JPG || targetFormat == SupportedFormat.PNG || targetFormat == SupportedFormat.WEBP)) {
            return imageEngine
        }

        // 5. Si los orígenes son video y el destino es GIF / JPG / PNG (extracción de frames) -> MediaEngine
        val allVideos = sourceFormats.isNotEmpty() && sourceFormats.all { it.isVideo }
        if (allVideos && targetFormat in setOf(SupportedFormat.GIF, SupportedFormat.JPG, SupportedFormat.PNG)) {
            return mediaEngine
        }

        // 6. Si los orígenes son documentos/PDF (o lote mixto de imágenes + documentos) y el destino es documental (o CSV/JSON desde XLSX) -> DocumentEngine
        val allDocsOrImages = sourceFormats.isNotEmpty() &&
            sourceFormats.all { it.category == FormatCategory.DOCUMENT || it.category == FormatCategory.IMAGE }
        if (allDocsOrImages &&
            (targetFormat.category == FormatCategory.DOCUMENT ||
                (sourceFormats.all { it == SupportedFormat.XLSX } && targetFormat in setOf(SupportedFormat.CSV, SupportedFormat.JSON)))
        ) {
            return documentEngine
        }

        // 7. Si los orígenes son audio/video -> MediaEngine
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
