package com.anywall.converter.domain.model

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Especificación individual de edición visual de una página de PDF en vista previa (v0.2).
 * Permite reordenar páginas entre uno o varios PDFs, rotar páginas individuales (0°, 90°, 180°, 270°)
 * y excluir/incluir páginas específicas antes de generar el archivo final.
 */
data class PdfPageEditItem(
    val id: String,
    val sourceUriString: String,
    val sourceDisplayName: String,
    val originalPageIndex: Int,
    val totalPagesInSource: Int,
    val rotationDegrees: Int = 0,
    val included: Boolean = true
) {
    val normalizedRotation: Int
        get() = ((rotationDegrees % 360) + 360) % 360

    val displayPageNumber: Int
        get() = originalPageIndex + 1

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
                    if (item.included) "1" else "0"
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

                result.add(
                    PdfPageEditItem(
                        id = id,
                        sourceUriString = sourceUri,
                        sourceDisplayName = sourceName,
                        originalPageIndex = pageIndex,
                        totalPagesInSource = totalPages,
                        rotationDegrees = rotation,
                        included = included
                    )
                )
            }
            return result
        }
    }
}
