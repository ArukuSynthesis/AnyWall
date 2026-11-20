package com.anywall.converter.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Acción secundaria encadenada automáticamente tras completar el paso 1 de conversión (Pipeline de 2 pasos - v0.3).
 */
enum class SecondaryPipelineAction(val labelEs: String, val labelEn: String) {
    NONE("Sin paso adicional", "No secondary step"),
    PACK_ZIP("Empaquetar salidas en ZIP", "Pack outputs into ZIP"),
    PACK_TAR_XZ("Empaquetar salidas en TAR.XZ", "Pack outputs into TAR.XZ"),
    ENCRYPT_AES256("Cifrar en Bóveda AES-256 (.anywall)", "Encrypt into AES-256 Vault (.anywall)"),
    COMPUTE_SHA256_MANIFEST("Adjuntar manifiesto SHA-256", "Attach SHA-256 manifest");

    fun localizedLabel(isEnglish: Boolean): String = if (isEnglish) labelEn else labelEs

    companion object {
        fun fromNameOrDefault(name: String?): SecondaryPipelineAction {
            if (name.isNullOrBlank()) return NONE
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: NONE
        }
    }
}

/**
 * Nivel de compresión para archivos ZIP, TAR.GZ, TAR.XZ y TAR.BZ2 (v0.3).
 */
enum class ArchiveCompressionLevel(val labelEs: String, val zipLevel: Int, val xzPreset: Int) {
    FAST("Rápido", 1, 2),
    BALANCED("Equilibrado", 6, 5),
    MAXIMUM("Máximo", 9, 7);

    companion object {
        fun fromNameOrDefault(name: String?): ArchiveCompressionLevel {
            if (name.isNullOrBlank()) return BALANCED
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: BALANCED
        }
    }
}

/**
 * Modos de composición por lote de imágenes (Individual, Collage en cuadrícula o Tira continua - v0.3).
 */
enum class ImageBatchLayoutMode(val labelEs: String) {
    INDIVIDUAL("Individual"),
    COLLAGE_GRID_2X2("Collage 2×2"),
    COLLAGE_GRID_3X3("Collage 3×3"),
    STITCH_VERTICAL("Tira Vertical"),
    STITCH_HORIZONTAL("Tira Horizontal");

    companion object {
        fun fromNameOrDefault(name: String?): ImageBatchLayoutMode {
            if (name.isNullOrBlank()) return INDIVIDUAL
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: INDIVIDUAL
        }
    }
}

/**
 * Filtros rápidos aplicables localmente a imágenes en `ImageEngineImpl` y `ImageVisualEditorDialog` (v0.3).
 */
enum class ImageFilterPreset(val labelEs: String) {
    NONE("Original"),
    GRAYSCALE("Escala de grises"),
    DOCUMENT_BW_HIGH_CONTRAST("Escáner B/N Alto Contraste"),
    INVERT("Invertir colores");

    companion object {
        fun fromNameOrDefault(name: String?): ImageFilterPreset {
            if (name.isNullOrBlank()) return NONE
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: NONE
        }
    }
}

/**
 * Ajuste de relación de aspecto con fondo difuminado (Blur) o color sólido (v0.3).
 */
enum class ImageAspectCanvasMode(val labelEs: String, val ratioWidth: Int, val ratioHeight: Int, val useBlur: Boolean) {
    ORIGINAL("Original", 0, 0, false),
    SQUARE_1_1_BLUR("1:1 Fondo Blur", 1, 1, true),
    PORTRAIT_4_5_BLUR("4:5 Fondo Blur", 4, 5, true),
    LANDSCAPE_16_9_BLUR("16:9 Fondo Blur", 16, 9, true),
    SQUARE_1_1_SOLID("1:1 Fondo Blanco", 1, 1, false);

    companion object {
        fun fromNameOrDefault(name: String?): ImageAspectCanvasMode {
            if (name.isNullOrBlank()) return ORIGINAL
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ORIGINAL
        }
    }
}

/**
 * Opciones de codificación de texto para conversión/reparación de archivos de texto y CSV (v0.3).
 */
enum class TextEncodingOption(val label: String, val charsetName: String) {
    UTF_8("UTF-8", "UTF-8"),
    ISO_8859_1("ISO-8859-1 (Latin-1)", "ISO-8859-1"),
    WINDOWS_1252("Windows-1252", "windows-1252"),
    UTF_16("UTF-16", "UTF-16");

    companion object {
        fun fromNameOrDefault(name: String?): TextEncodingOption {
            if (name.isNullOrBlank()) return UTF_8
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: UTF_8
        }
    }
}

/**
 * Normalización de saltos de línea en archivos de texto (v0.3).
 */
enum class LineEndingOption(val label: String) {
    KEEP("Conservar"),
    LF("LF (Unix/Android \\n)"),
    CRLF("CRLF (Windows \\r\\n)");

    companion object {
        fun fromNameOrDefault(name: String?): LineEndingOption {
            if (name.isNullOrBlank()) return KEEP
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: KEEP
        }
    }
}

/**
 * Bitrate objetivo para codificación de audio MP3, AAC y M4A (v0.3).
 */
enum class AudioBitrateKbps(val kbps: Int, val label: String) {
    KBPS_128(128, "128 kbps"),
    KBPS_192(192, "192 kbps"),
    KBPS_320(320, "320 kbps");

    companion object {
        fun fromKbpsOrDefault(kbps: Int): AudioBitrateKbps {
            return entries.firstOrNull { it.kbps == kbps } ?: KBPS_192
        }
    }
}

/**
 * Velocidad de reproducción de video/audio en `MediaEngineImpl` (v0.3).
 */
enum class VideoPlaybackSpeed(val factor: Float, val label: String) {
    SPEED_0_5X(0.5f, "0.5x"),
    SPEED_0_75X(0.75f, "0.75x"),
    SPEED_1_0X(1.0f, "1.0x"),
    SPEED_1_25X(1.25f, "1.25x"),
    SPEED_1_5X(1.5f, "1.5x"),
    SPEED_2_0X(2.0f, "2.0x");

    companion object {
        fun fromFactorOrDefault(factor: Float): VideoPlaybackSpeed {
            return entries.minByOrNull { kotlin.math.abs(it.factor - factor) } ?: SPEED_1_0X
        }
    }
}

/**
 * Resolutor determinista de plantillas de renombrado dinámico por lote (v0.3).
 * Soporta los marcadores:
 * - `{nombre}` / `{name}`: nombre base original sin extensión
 * - `{indice}` / `{index}`: número secuencial en el lote (`01`, `02`, ...)
 * - `{fecha}` / `{date}`: fecha actual (`yyyyMMdd`)
 * - `{formato}` / `{format}`: extensión de salida en minúsculas
 */
object OutputNamingTemplateResolver {

    fun hasTemplatePlaceholders(template: String): Boolean {
        val lower = template.lowercase(Locale.ROOT)
        return lower.contains("{nombre}") ||
            lower.contains("{name}") ||
            lower.contains("{indice}") ||
            lower.contains("{index}") ||
            lower.contains("{fecha}") ||
            lower.contains("{date}") ||
            lower.contains("{formato}") ||
            lower.contains("{format}")
    }

    fun resolveBaseName(
        templateOrCustomName: String,
        originalBaseName: String,
        batchIndexOneBased: Int,
        batchTotalCount: Int,
        targetFormat: SupportedFormat,
        fixedDateString: String? = null
    ): String {
        val trimmed = templateOrCustomName.trim()
        if (trimmed.isEmpty()) {
            return originalBaseName.ifBlank { "anywall_archivo" }
        }

        val dateStr = fixedDateString ?: SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val padWidth = if (batchTotalCount >= 100) 3 else 2
        val indexStr = batchIndexOneBased.coerceAtLeast(1).toString().padStart(padWidth, '0')
        val cleanOrig = originalBaseName.ifBlank { "archivo" }
        val fmtExt = targetFormat.extension.lowercase(Locale.ROOT)

        if (hasTemplatePlaceholders(trimmed)) {
            return trimmed
                .replace(Regex("\\{nombre\\}", RegexOption.IGNORE_CASE), cleanOrig)
                .replace(Regex("\\{name\\}", RegexOption.IGNORE_CASE), cleanOrig)
                .replace(Regex("\\{indice\\}", RegexOption.IGNORE_CASE), indexStr)
                .replace(Regex("\\{index\\}", RegexOption.IGNORE_CASE), indexStr)
                .replace(Regex("\\{fecha\\}", RegexOption.IGNORE_CASE), dateStr)
                .replace(Regex("\\{date\\}", RegexOption.IGNORE_CASE), dateStr)
                .replace(Regex("\\{formato\\}", RegexOption.IGNORE_CASE), fmtExt)
                .replace(Regex("\\{format\\}", RegexOption.IGNORE_CASE), fmtExt)
                .trim()
                .ifBlank { cleanOrig }
        }

        return if (batchTotalCount <= 1) {
            trimmed
        } else {
            "${trimmed}_$indexStr"
        }
    }
}
