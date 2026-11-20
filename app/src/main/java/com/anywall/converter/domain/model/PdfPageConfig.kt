package com.anywall.converter.domain.model

/**
 * Tamaños de página estándar para generación de documentos PDF en AnyWall v0.2.
 */
enum class PdfPageSize(
    val labelEs: String,
    val widthPt: Int,
    val heightPt: Int
) {
    A4("A4", 595, 842),
    LETTER("Carta (Letter)", 612, 792);

    companion object {
        fun fromNameOrDefault(name: String?): PdfPageSize {
            if (name.isNullOrBlank()) return A4
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: A4
        }
    }
}

/**
 * Orientación de página para generación de documentos PDF.
 */
enum class PdfPageOrientation(val labelEs: String) {
    PORTRAIT("Vertical"),
    LANDSCAPE("Horizontal");

    companion object {
        fun fromNameOrDefault(name: String?): PdfPageOrientation {
            if (name.isNullOrBlank()) return PORTRAIT
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: PORTRAIT
        }
    }
}
