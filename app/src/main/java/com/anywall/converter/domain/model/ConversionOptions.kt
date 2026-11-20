package com.anywall.converter.domain.model

/**
 * Parámetros configurables para cualquier lote de conversión en AnyWall (v0.2).
 */
data class ConversionOptions(
    val qualityPercent: Int = 85,
    val maxDimensionPx: Int? = null,
    val imageRotationDegrees: Int = 0,
    val stripExifMetadata: Boolean = true,
    val videoResolution: VideoResolution = VideoResolution.ORIGINAL,
    val muteVideoAudio: Boolean = false,
    val trimStartSeconds: Long? = null,
    val trimEndSeconds: Long? = null,
    val customOutputBaseName: String = "",
    val mergeIntoSinglePdf: Boolean = true,
    val pdfActionMode: PdfActionMode = PdfActionMode.MERGE_OR_CONVERT,
    val pageRangeSpec: String = "",
    val pdfPassword: String = "",
    val pdfPageSize: PdfPageSize = PdfPageSize.A4,
    val pdfPageOrientation: PdfPageOrientation = PdfPageOrientation.PORTRAIT,
    val pdfVisualPageEditsJson: String = ""
) {
    val clampedQuality: Int
        get() = qualityPercent.coerceIn(10, 100)

    val normalizedRotationDegrees: Int
        get() = ((imageRotationDegrees % 360) + 360) % 360

    val hasValidTrimRange: Boolean
        get() {
            val start = trimStartSeconds ?: 0L
            val end = trimEndSeconds
            return (start > 0L && (end == null || end > start)) || (end != null && end > start && end > 0L)
        }

    val hasVisualPdfEdits: Boolean
        get() = pdfVisualPageEditsJson.isNotBlank()
}
