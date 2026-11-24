package com.anywall.converter.domain.model

/**
 * Categorías principales de archivos soportadas por AnyWall: Universal Converter (v0.4).
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
        descriptionEs = "JPG, PNG, WEBP, BMP, SVG, HEIC, AVIF, GIF, ICO, Collages ↔ PDF / CBZ",
        displayNameEn = "Images",
        descriptionEn = "JPG, PNG, WEBP, BMP, SVG, HEIC, AVIF, GIF, ICO, Collages ↔ PDF / CBZ",
        mimeFilters = arrayOf("image/*", "application/pdf")
    ),
    DOCUMENT(
        displayNameEs = "Documentos / PDF",
        descriptionEs = "PDF (Editor Táctil/Firma/Anotaciones), DOCX, XLSX, EPUB, CBZ, CBR, HTML, TXT, MD",
        displayNameEn = "Documents / PDF",
        descriptionEn = "PDF (Touch Editor/Sign/Annotate), DOCX, XLSX, EPUB, CBZ, CBR, HTML, TXT, MD",
        mimeFilters = arrayOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/epub+zip",
            "application/vnd.comicbook+zip",
            "application/vnd.comicbook-rar",
            "text/html",
            "text/plain",
            "text/markdown",
            "text/*"
        )
    ),
    MEDIA(
        displayNameEs = "Audio / Video",
        descriptionEs = "MP4, MKV, MOV, WEBM, MP3, AAC, M4A, WAV, FLAC, OGG, OPUS (Trim, Crop, ID3, Auto-Gain)",
        displayNameEn = "Audio / Video",
        descriptionEn = "MP4, MKV, MOV, WEBM, MP3, AAC, M4A, WAV, FLAC, OGG, OPUS (Trim, Crop, ID3, Auto-Gain)",
        mimeFilters = arrayOf("video/*", "audio/*")
    ),
    ARCHIVE(
        displayNameEs = "Comprimidos",
        descriptionEs = "RAR / RAR5 (Extraer/Re-empaquetar), ZIP, 7Z, TAR.GZ, TAR.XZ, TAR.BZ2, CBZ, Partes",
        displayNameEn = "Archives",
        descriptionEn = "RAR / RAR5 (Extract/Repack), ZIP, 7Z, TAR.GZ, TAR.XZ, TAR.BZ2, CBZ, Parts",
        mimeFilters = arrayOf(
            "application/zip",
            "application/vnd.rar",
            "application/x-rar-compressed",
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
        descriptionEs = "CSV, JSON, XML, YAML, Base64, Bóveda AES-256 (.anywall), QR, Duplicados SHA-256",
        displayNameEn = "Data & Security",
        descriptionEn = "CSV, JSON, XML, YAML, Base64, AES-256 Vault (.anywall), QR, SHA-256 Duplicates",
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
