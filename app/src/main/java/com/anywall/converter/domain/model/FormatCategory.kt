package com.anywall.converter.domain.model

/**
 * Categorías principales de archivos soportadas por AnyWall: Universal Converter.
 */
enum class FormatCategory(
    val displayNameEs: String,
    val descriptionEs: String,
    val mimeFilters: Array<String>
) {
    IMAGE(
        displayNameEs = "Imágenes",
        descriptionEs = "JPG, PNG, WEBP, BMP, SVG ↔ PDF",
        mimeFilters = arrayOf("image/*", "application/pdf")
    ),
    DOCUMENT(
        displayNameEs = "Documentos / PDF",
        descriptionEs = "PDF, DOCX, HTML, TXT, Markdown",
        mimeFilters = arrayOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/html",
            "text/plain",
            "text/markdown",
            "text/*"
        )
    ),
    MEDIA(
        displayNameEs = "Audio / Video",
        descriptionEs = "MP4, MKV, MOV, WEBM, MP3, AAC, M4A, WAV",
        mimeFilters = arrayOf("video/*", "audio/*")
    ),
    ARCHIVE(
        displayNameEs = "Comprimidos",
        descriptionEs = "ZIP, TAR, TAR.GZ, GZ, 7Z",
        mimeFilters = arrayOf(
            "application/zip",
            "application/x-tar",
            "application/gzip",
            "application/x-gzip",
            "application/x-gtar",
            "application/x-7z-compressed",
            "application/octet-stream",
            "*/*"
        )
    )
}
