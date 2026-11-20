package com.anywall.converter.domain.model

import java.util.Locale

/**
 * Formatos de archivo de entrada y salida soportados de manera 100% local por AnyWall (v0.2).
 */
enum class SupportedFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
    val category: FormatCategory,
    val aliases: Set<String> = emptySet(),
    val isVideo: Boolean = false,
    val isAudio: Boolean = false,
    val isArchiveOutput: Boolean = false,
    val isExtractionTarget: Boolean = false
) {
    // Imágenes
    JPG(
        label = "JPG",
        extension = "jpg",
        mimeType = "image/jpeg",
        category = FormatCategory.IMAGE,
        aliases = setOf("jpeg", "jfif")
    ),
    PNG(
        label = "PNG",
        extension = "png",
        mimeType = "image/png",
        category = FormatCategory.IMAGE
    ),
    WEBP(
        label = "WEBP",
        extension = "webp",
        mimeType = "image/webp",
        category = FormatCategory.IMAGE
    ),
    BMP(
        label = "BMP",
        extension = "bmp",
        mimeType = "image/bmp",
        category = FormatCategory.IMAGE,
        aliases = setOf("dib")
    ),
    SVG(
        label = "SVG",
        extension = "svg",
        mimeType = "image/svg+xml",
        category = FormatCategory.IMAGE
    ),
    HEIC(
        label = "HEIC",
        extension = "heic",
        mimeType = "image/heic",
        category = FormatCategory.IMAGE,
        aliases = setOf("heif")
    ),
    GIF(
        label = "GIF",
        extension = "gif",
        mimeType = "image/gif",
        category = FormatCategory.IMAGE
    ),

    // Documentos y PDF
    PDF(
        label = "PDF",
        extension = "pdf",
        mimeType = "application/pdf",
        category = FormatCategory.DOCUMENT
    ),
    DOCX(
        label = "DOCX",
        extension = "docx",
        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        category = FormatCategory.DOCUMENT
    ),
    HTML(
        label = "HTML",
        extension = "html",
        mimeType = "text/html",
        category = FormatCategory.DOCUMENT,
        aliases = setOf("htm", "xhtml")
    ),
    TXT(
        label = "TXT",
        extension = "txt",
        mimeType = "text/plain",
        category = FormatCategory.DOCUMENT,
        aliases = setOf("log", "csv")
    ),
    MD(
        label = "MD",
        extension = "md",
        mimeType = "text/markdown",
        category = FormatCategory.DOCUMENT,
        aliases = setOf("markdown")
    ),

    // Video
    MP4(
        label = "MP4",
        extension = "mp4",
        mimeType = "video/mp4",
        category = FormatCategory.MEDIA,
        aliases = setOf("m4v"),
        isVideo = true
    ),
    MKV(
        label = "MKV",
        extension = "mkv",
        mimeType = "video/x-matroska",
        category = FormatCategory.MEDIA,
        isVideo = true
    ),
    MOV(
        label = "MOV",
        extension = "mov",
        mimeType = "video/quicktime",
        category = FormatCategory.MEDIA,
        isVideo = true
    ),
    WEBM(
        label = "WEBM",
        extension = "webm",
        mimeType = "video/webm",
        category = FormatCategory.MEDIA,
        isVideo = true
    ),

    // Audio
    MP3(
        label = "MP3",
        extension = "mp3",
        mimeType = "audio/mpeg",
        category = FormatCategory.MEDIA,
        isAudio = true
    ),
    AAC(
        label = "AAC",
        extension = "aac",
        mimeType = "audio/aac",
        category = FormatCategory.MEDIA,
        isAudio = true
    ),
    M4A(
        label = "M4A",
        extension = "m4a",
        mimeType = "audio/mp4",
        category = FormatCategory.MEDIA,
        isAudio = true
    ),
    WAV(
        label = "WAV",
        extension = "wav",
        mimeType = "audio/wav",
        category = FormatCategory.MEDIA,
        aliases = setOf("wave"),
        isAudio = true
    ),

    // Archivos comprimidos
    ZIP(
        label = "ZIP",
        extension = "zip",
        mimeType = "application/zip",
        category = FormatCategory.ARCHIVE,
        isArchiveOutput = true
    ),
    TAR(
        label = "TAR",
        extension = "tar",
        mimeType = "application/x-tar",
        category = FormatCategory.ARCHIVE,
        isArchiveOutput = true
    ),
    TAR_GZ(
        label = "TAR.GZ",
        extension = "tar.gz",
        mimeType = "application/gzip",
        category = FormatCategory.ARCHIVE,
        aliases = setOf("tgz"),
        isArchiveOutput = true
    ),
    TAR_XZ(
        label = "TAR.XZ",
        extension = "tar.xz",
        mimeType = "application/x-xz",
        category = FormatCategory.ARCHIVE,
        aliases = setOf("txz"),
        isArchiveOutput = true
    ),
    TAR_BZ2(
        label = "TAR.BZ2",
        extension = "tar.bz2",
        mimeType = "application/x-bzip2",
        category = FormatCategory.ARCHIVE,
        aliases = setOf("tbz2", "tbz"),
        isArchiveOutput = true
    ),
    GZ(
        label = "GZ",
        extension = "gz",
        mimeType = "application/gzip",
        category = FormatCategory.ARCHIVE
    ),
    XZ(
        label = "XZ",
        extension = "xz",
        mimeType = "application/x-xz",
        category = FormatCategory.ARCHIVE
    ),
    BZ2(
        label = "BZ2",
        extension = "bz2",
        mimeType = "application/x-bzip2",
        category = FormatCategory.ARCHIVE,
        aliases = setOf("bzip2")
    ),
    SEVEN_Z(
        label = "7Z",
        extension = "7z",
        mimeType = "application/x-7z-compressed",
        category = FormatCategory.ARCHIVE
    ),
    EXTRACT_ARCHIVE(
        label = "EXTRAER",
        extension = "folder",
        mimeType = "resource/folder",
        category = FormatCategory.ARCHIVE,
        isExtractionTarget = true
    );

    companion object {
        /**
         * Detecta el formato a partir del nombre del archivo y/o su tipo MIME.
         */
        fun detect(fileName: String?, mimeType: String?): SupportedFormat? {
            val lowerName = fileName?.lowercase(Locale.ROOT)?.trim().orEmpty()
            when {
                lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz") -> return TAR_GZ
                lowerName.endsWith(".tar.xz") || lowerName.endsWith(".txz") -> return TAR_XZ
                lowerName.endsWith(".tar.bz2") || lowerName.endsWith(".tbz2") || lowerName.endsWith(".tbz") -> return TAR_BZ2
            }

            val ext = lowerName.substringAfterLast('.', missingDelimiterValue = "")
            if (ext.isNotEmpty()) {
                entries.firstOrNull { format ->
                    !format.isExtractionTarget &&
                        (format.extension.equals(ext, ignoreCase = true) ||
                            format.aliases.any { it.equals(ext, ignoreCase = true) })
                }?.let { return it }
            }

            val cleanMime = mimeType?.lowercase(Locale.ROOT)?.substringBefore(';')?.trim().orEmpty()
            if (cleanMime.isNotEmpty()) {
                when (cleanMime) {
                    "image/jpeg", "image/jpg" -> return JPG
                    "image/png" -> return PNG
                    "image/webp" -> return WEBP
                    "image/bmp", "image/x-ms-bmp" -> return BMP
                    "image/svg+xml" -> return SVG
                    "image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence" -> return HEIC
                    "image/gif" -> return GIF
                    "application/pdf" -> return PDF
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> return DOCX
                    "text/html", "application/xhtml+xml" -> return HTML
                    "text/markdown", "text/x-markdown" -> return MD
                    "text/plain" -> return TXT
                    "video/mp4" -> return MP4
                    "video/x-matroska", "video/matroska" -> return MKV
                    "video/quicktime" -> return MOV
                    "video/webm" -> return WEBM
                    "audio/mpeg", "audio/mp3" -> return MP3
                    "audio/aac", "audio/aacp", "audio/x-aac" -> return AAC
                    "audio/mp4", "audio/x-m4a", "audio/m4a" -> return M4A
                    "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave" -> return WAV
                    "application/zip", "application/x-zip-compressed" -> return ZIP
                    "application/x-tar", "application/tar" -> return TAR
                    "application/x-gtar", "application/x-tgz" -> return TAR_GZ
                    "application/gzip", "application/x-gzip" -> return GZ
                    "application/x-xz" -> return XZ
                    "application/x-bzip2", "application/bzip2" -> return BZ2
                    "application/x-7z-compressed" -> return SEVEN_Z
                }
            }
            return null
        }

        fun fromNameOrNull(name: String?): SupportedFormat? {
            if (name.isNullOrBlank()) return null
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }
}
