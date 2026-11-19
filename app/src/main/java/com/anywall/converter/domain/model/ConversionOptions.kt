package com.anywall.converter.domain.model

/**
 * Parámetros configurables para cualquier lote de conversión en AnyWall.
 */
data class ConversionOptions(
    val qualityPercent: Int = 85,
    val maxDimensionPx: Int? = null,
    val videoResolution: VideoResolution = VideoResolution.ORIGINAL,
    val customOutputBaseName: String = "",
    val mergeIntoSinglePdf: Boolean = true,
    val pdfActionMode: PdfActionMode = PdfActionMode.MERGE_OR_CONVERT,
    val pageRangeSpec: String = ""
) {
    val clampedQuality: Int
        get() = qualityPercent.coerceIn(10, 100)
}
