package com.anywall.converter.domain.model

/**
 * Modos de operación especializada sobre archivos PDF (v0.3).
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
        labelEs = "Dividir todas las páginas (1 por PDF)",
        descriptionEs = "Genera un archivo PDF independiente por cada página del documento"
    ),
    SPLIT_BY_CHUNK(
        labelEs = "Dividir cada N páginas",
        descriptionEs = "Divide automáticamente el PDF en bloques de N páginas por archivo"
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
    ),
    EXTRACT_EMBEDDED_IMAGES(
        labelEs = "Extraer imágenes originales del PDF",
        descriptionEs = "Extrae todas las fotos e imágenes incrustadas en su resolución original"
    ),
    BOOKLET_N_UP_2(
        labelEs = "Modo Folleto (2 páginas por hoja)",
        descriptionEs = "Agrupa 2 páginas lado a lado en cada hoja para ahorrar papel al imprimir"
    ),
    BOOKLET_N_UP_4(
        labelEs = "Modo Cuadrícula (4 páginas por hoja)",
        descriptionEs = "Agrupa 4 páginas (2x2) en cada hoja A4/Carta"
    );

    companion object {
        fun fromNameOrDefault(name: String?): PdfActionMode {
            if (name.isNullOrBlank()) return MERGE_OR_CONVERT
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MERGE_OR_CONVERT
        }
    }
}
