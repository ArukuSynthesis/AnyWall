package com.anywall.converter.domain.model

/**
 * Resoluciones de salida soportadas por MediaEngineImpl con Media3 Transformer.
 */
enum class VideoResolution(
    val labelEs: String,
    val targetHeightPx: Int?
) {
    ORIGINAL("Original", null),
    FHD_1080P("1080p", 1080),
    HD_720P("720p", 720),
    SD_480P("480p", 480);

    companion object {
        fun fromNameOrDefault(name: String?): VideoResolution {
            if (name.isNullOrBlank()) return ORIGINAL
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ORIGINAL
        }
    }
}
