package com.anywall.converter.domain.model

/**
 * Modos de operación especializada sobre archivos PDF (v0.2).
 */
enum class PdfActionMode(val labelEs: String, val descriptionEs: String) {
    MERGE_OR_CONVERT(
        labelEs = "Unir / Convertir a PDF",
        descriptionEs = "Combina múltiples archivos en un único PDF o convierte al formato destino"
    ),
    SPLIT_RANGE(
        labelEs = "Extraer rango de páginas",
        descriptionEs = "Extrae un rango específico de páginas (ej. 1-3, 5) en un nuevo PDF"
    ),
    SPLIT_ALL_PAGES(
        labelEs = "Dividir todas las páginas",
        descriptionEs = "Genera un archivo PDF independiente por cada página del documento"
    ),
    OPTIMIZE_COMPRESS(
        labelEs = "Optimizar y comprimir PDF",
        descriptionEs = "Reescribe y comprime las páginas del PDF según el porcentaje de calidad seleccionado"
    ),
    PROTECT_PASSWORD(
        labelEs = "Proteger con contraseña",
        descriptionEs = "Cifra el documento PDF localmente con contraseña mediante estándar PDFBox"
    ),
    UNLOCK_PASSWORD(
        labelEs = "Desbloquear PDF",
        descriptionEs = "Elimina la protección por contraseña de un PDF proporcionando su clave actual"
    );

    companion object {
        fun fromNameOrDefault(name: String?): PdfActionMode {
            if (name.isNullOrBlank()) return MERGE_OR_CONVERT
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MERGE_OR_CONVERT
        }
    }
}
