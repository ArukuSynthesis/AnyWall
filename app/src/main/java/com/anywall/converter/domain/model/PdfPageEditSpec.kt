package com.anywall.converter.domain.model

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * Rectángulo normalizado `[0.0 .. 1.0]` de censura o pixelado aplicable sobre una página PDF o una imagen (v0.3).
 */
data class NormalizedRedactionBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val isPixelated: Boolean = false
) {
    val clampedLeft: Float get() = minOf(left, right).coerceIn(0f, 1f)
    val clampedTop: Float get() = minOf(top, bottom).coerceIn(0f, 1f)
    val clampedRight: Float get() = maxOf(left, right).coerceIn(0f, 1f)
    val clampedBottom: Float get() = maxOf(top, bottom).coerceIn(0f, 1f)

    fun toToken(): String {
        return String.format(
            Locale.US,
            "%.4f,%.4f,%.4f,%.4f,%d",
            clampedLeft,
            clampedTop,
            clampedRight,
            clampedBottom,
            if (isPixelated) 1 else 0
        )
    }

    companion object {
        fun fromToken(token: String): NormalizedRedactionBox? {
            val parts = token.split(',')
            if (parts.size < 4) return null
            val l = parts[0].toFloatOrNull() ?: return null
            val t = parts[1].toFloatOrNull() ?: return null
            val r = parts[2].toFloatOrNull() ?: return null
            val b = parts[3].toFloatOrNull() ?: return null
            val pix = parts.getOrNull(4) == "1"
            return NormalizedRedactionBox(
                left = l,
                top = t,
                right = r,
                bottom = b,
                isPixelated = pix
            )
        }

        fun serializeList(boxes: List<NormalizedRedactionBox>): String {
            if (boxes.isEmpty()) return ""
            return boxes.joinToString(separator = ";") { it.toToken() }
        }

        fun deserializeList(raw: String?): List<NormalizedRedactionBox> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(';').mapNotNull { fromToken(it.trim()) }
        }
    }
}

/**
 * Especificación individual de edición visual de una página de PDF en vista previa (v0.3).
 * Soporta reordenamiento entre PDFs, rotación (0°/90°/180°/270°), exclusión/inclusión,
 * inserción de páginas separadoras en blanco, duplicación de páginas, estampado de firma manuscrita
 * y rectángulos de censura sobre la página.
 */
data class PdfPageEditItem(
    val id: String,
    val sourceUriString: String,
    val sourceDisplayName: String,
    val originalPageIndex: Int,
    val totalPagesInSource: Int,
    val rotationDegrees: Int = 0,
    val included: Boolean = true,
    val isBlankSeparator: Boolean = false,
    val hasSignatureStamp: Boolean = false,
    val redactionBoxes: List<NormalizedRedactionBox> = emptyList()
) {
    val normalizedRotation: Int
        get() = ((rotationDegrees % 360) + 360) % 360

    val displayPageNumber: Int
        get() = if (isBlankSeparator) 0 else (originalPageIndex + 1)

    companion object {
        private fun enc(value: String): String =
            URLEncoder.encode(value, Charsets.UTF_8.name())

        private fun dec(value: String): String =
            URLDecoder.decode(value, Charsets.UTF_8.name())

        /**
         * Serializa la lista de ediciones de páginas en un formato determinista 100% Kotlin puro
         * (compatible tanto con Android Runtime como con pruebas unitarias JVM locales).
         */
        fun toJsonArrayString(items: List<PdfPageEditItem>): String {
            if (items.isEmpty()) return ""
            return items.joinToString(separator = "\n") { item ->
                listOf(
                    enc(item.id),
                    enc(item.sourceUriString),
                    enc(item.sourceDisplayName),
                    item.originalPageIndex.toString(),
                    item.totalPagesInSource.toString(),
                    item.normalizedRotation.toString(),
                    if (item.included) "1" else "0",
                    if (item.isBlankSeparator) "1" else "0",
                    if (item.hasSignatureStamp) "1" else "0",
                    enc(NormalizedRedactionBox.serializeList(item.redactionBoxes))
                ).joinToString(separator = "\t")
            }
        }

        fun fromJsonArrayString(rawSerialized: String?): List<PdfPageEditItem> {
            if (rawSerialized.isNullOrBlank()) return emptyList()
            val lines = rawSerialized.lines().filter { it.isNotBlank() }
            val result = ArrayList<PdfPageEditItem>(lines.size)
            for ((idx, line) in lines.withIndex()) {
                val parts = line.split('\t')
                if (parts.size < 7) continue
                val id = dec(parts[0]).ifBlank { "page_$idx" }
                val sourceUri = dec(parts[1])
                val sourceName = dec(parts[2]).ifBlank { "PDF" }
                val pageIndex = parts[3].toIntOrNull() ?: continue
                val totalPages = parts[4].toIntOrNull() ?: 1
                val rotation = parts[5].toIntOrNull() ?: 0
                val included = parts[6] == "1" || parts[6].equals("true", ignoreCase = true)
                val isBlank = parts.getOrNull(7) == "1"
                val hasSig = parts.getOrNull(8) == "1"
                val boxes = NormalizedRedactionBox.deserializeList(
                    parts.getOrNull(9)?.let { dec(it) }
                )

                result.add(
                    PdfPageEditItem(
                        id = id,
                        sourceUriString = sourceUri,
                        sourceDisplayName = sourceName,
                        originalPageIndex = pageIndex,
                        totalPagesInSource = totalPages,
                        rotationDegrees = rotation,
                        included = included,
                        isBlankSeparator = isBlank,
                        hasSignatureStamp = hasSig,
                        redactionBoxes = boxes
                    )
                )
            }
            return result
        }
    }
}
