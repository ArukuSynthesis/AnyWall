package com.anywall.converter.domain.model

/**
 * Categorías principales de archivos soportadas por AnyWall: Universal Converter (v0.3).
 */
enum class FormatCategory(
    val displayNameEs: String,
    val descriptionEs: String,
    val displayNameEn: String,
    val descriptionEn: String,
    val mimeFilters: Array<String>
) {
    IMAGE(
        displayNameEs = "Imágenes",
        descriptionEs = "JPG, PNG, WEBP, BMP, SVG, HEIC, AVIF, GIF, ICO, Collages ↔ PDF",
        displayNameEn = "Images",
        descriptionEn = "JPG, PNG, WEBP, BMP, SVG, HEIC, AVIF, GIF, ICO, Collages ↔ PDF",
        mimeFilters = arrayOf("image/*", "application/pdf")
    ),
    DOCUMENT(
        displayNameEs = "Documentos / PDF",
        descriptionEs = "PDF (Editor/Firma/N-up/Cifrar), DOCX, XLSX, EPUB, HTML, TXT, MD",
        displayNameEn = "Documents / PDF",
        descriptionEn = "PDF (Editor/Sign/N-up/Encrypt), DOCX, XLSX, EPUB, HTML, TXT, MD",
        mimeFilters = arrayOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/epub+zip",
            "text/html",
            "text/plain",
            "text/markdown",
            "text/*"
        )
    ),
    MEDIA(
        displayNameEs = "Audio / Video",
        descriptionEs = "MP4, MKV, MOV, WEBM, MP3, AAC, M4A, WAV, FLAC, OGG, OPUS",
        displayNameEn = "Audio / Video",
        descriptionEn = "MP4, MKV, MOV, WEBM, MP3, AAC, M4A, WAV, FLAC, OGG, OPUS",
        mimeFilters = arrayOf("video/*", "audio/*")
    ),
    ARCHIVE(
        displayNameEs = "Comprimidos",
        descriptionEs = "ZIP, TAR, TAR.GZ, TAR.XZ, TAR.BZ2, GZ, XZ, BZ2, 7Z, Partes (.part001)",
        displayNameEn = "Archives",
        descriptionEn = "ZIP, TAR, TAR.GZ, TAR.XZ, TAR.BZ2, GZ, XZ, BZ2, 7Z, Parts (.part001)",
        mimeFilters = arrayOf(
            "application/zip",
            "application/x-tar",
            "application/gzip",
            "application/x-gzip",
            "application/x-gtar",
            "application/x-xz",
            "application/x-bzip2",
            "application/x-7z-compressed",
            "application/octet-stream",
            "*/*"
        )
    ),
    DATA_TOOLS(
        displayNameEs = "Datos y Seguridad",
        descriptionEs = "CSV, JSON, XML, YAML, Base64, Bóveda AES-256 (.anywall), QR",
        displayNameEn = "Data & Security",
        descriptionEn = "CSV, JSON, XML, YAML, Base64, AES-256 Vault (.anywall), QR",
        mimeFilters = arrayOf(
            "text/csv",
            "application/json",
            "application/xml",
            "text/xml",
            "text/plain",
            "application/octet-stream",
            "*/*"
        )
    );

    fun localizedName(isEnglish: Boolean): String = if (isEnglish) displayNameEn else displayNameEs
    fun localizedDescription(isEnglish: Boolean): String = if (isEnglish) descriptionEn else descriptionEs
}
