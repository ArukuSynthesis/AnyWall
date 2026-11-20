package com.anywall.converter.core

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.SupportedFormat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.UUID

/**
 * Resolutor seguro de URIs (`content://`, árboles de carpetas SAF y `file://`) mediante `ContentResolver` (v0.2).
 * Extrae nombre real, tamaño, tipo MIME, detecta el `SupportedFormat` (incluyendo inspección
 * de cabeceras mágicas como fallback) y gestiona copias temporales en caché.
 */
class UriFileResolver(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Intenta adquirir permiso persistente de lectura si la URI proviene del selector SAF.
     */
    fun tryTakePersistableReadPermission(uri: Uri) {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Recorre una carpeta seleccionada mediante `ActivityResultContracts.OpenDocumentTree`
     * y devuelve la lista de URIs de todos los archivos encontrados (incluyendo subcarpetas de primer nivel).
     */
    fun resolveFolderTreeUris(treeUri: Uri, maxFiles: Int = 250): List<Uri> {
        tryTakePersistableReadPermission(treeUri)
        val collected = mutableListOf<Uri>()

        fun traverseFolder(parentDocId: String, depth: Int) {
            if (collected.size >= maxFiles || depth > 2) return
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            try {
                contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

                    while (cursor.moveToNext() && collected.size < maxFiles) {
                        val docId = if (idIdx >= 0) cursor.getString(idIdx) else null
                        if (docId.isNullOrBlank()) continue
                        val mime = if (mimeIdx >= 0 && !cursor.isNull(mimeIdx)) cursor.getString(mimeIdx) else ""
                        val name = if (nameIdx >= 0 && !cursor.isNull(nameIdx)) cursor.getString(nameIdx) else ""

                        if (name.startsWith(".")) continue

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            traverseFolder(docId, depth + 1)
                        } else {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            collected.add(docUri)
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }

        val rootDocId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Throwable) {
            null
        }
        if (!rootDocId.isNullOrBlank()) {
            traverseFolder(rootDocId, 0)
        }
        return collected
    }

    /**
     * Resuelve una URI a un `InputFileItem` listo para la cola de conversión.
     */
    fun resolveInputFile(uri: Uri): InputFileItem? {
        tryTakePersistableReadPermission(uri)

        var displayName: String? = null
        var sizeBytes = 0L

        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                            displayName = cursor.getString(nameIdx)
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            sizeBytes = cursor.getLong(sizeIdx)
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val file = uri.path?.let(::File)
            if (file != null && file.exists()) {
                displayName = file.name
                sizeBytes = file.length()
            }
        }

        if (sizeBytes <= 0L) {
            try {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.length > 0L) {
                        sizeBytes = afd.length
                    }
                }
            } catch (_: Throwable) {
            }
        }

        val rawMimeType = try {
            contentResolver.getType(uri)
        } catch (_: Throwable) {
            null
        }

        val fallbackName = displayName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "archivo_${System.currentTimeMillis()}"

        var detectedFormat = SupportedFormat.detect(fallbackName, rawMimeType)
        if (detectedFormat == null) {
            detectedFormat = sniffMagicHeader(uri)
        }
        if (detectedFormat == null) {
            val extFromMime = rawMimeType?.let {
                MimeTypeMap.getSingleton().getExtensionFromMimeType(it)
            }
            if (!extFromMime.isNullOrBlank()) {
                detectedFormat = SupportedFormat.detect("archivo.$extFromMime", rawMimeType)
            }
        }

        val finalFormat = detectedFormat ?: SupportedFormat.TXT
        val finalName = if (fallbackName.contains('.')) {
            fallbackName
        } else {
            "$fallbackName.${finalFormat.extension}"
        }
        val finalMime = rawMimeType?.takeIf { it.isNotBlank() } ?: finalFormat.mimeType

        return InputFileItem(
            uri = uri,
            displayName = sanitizeFileName(finalName),
            sizeBytes = sizeBytes.coerceAtLeast(0L),
            mimeType = finalMime,
            format = finalFormat
        )
    }

    @Throws(IOException::class)
    fun openInputStream(uri: Uri): InputStream {
        return when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> {
                val path = uri.path ?: throw IOException("Ruta de archivo vacía para URI: $uri")
                FileInputStream(File(path))
            }
            else -> contentResolver.openInputStream(uri)
                ?: throw IOException("No se pudo abrir el flujo de lectura para: $uri")
        }
    }

    @Throws(IOException::class)
    fun copyUriToTempFile(uri: Uri, prefix: String = "in_", extension: String = "tmp"): File {
        val workDir = getWorkingTempDir()
        val cleanExt = extension.trimStart('.').ifBlank { "tmp" }
        val tempFile = File(workDir, "${prefix}${UUID.randomUUID()}.$cleanExt")
        openInputStream(uri).use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
                output.flush()
            }
        }
        return tempFile
    }

    fun createTempWorkingFile(prefix: String = "out_", extension: String): File {
        val workDir = getWorkingTempDir()
        val cleanExt = extension.trimStart('.').ifBlank { "tmp" }
        return File(workDir, "${prefix}${UUID.randomUUID()}.$cleanExt")
    }

    fun getWorkingTempDir(): File {
        val dir = File(context.cacheDir, "anywall_work")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getTempCacheSizeBytes(): Long {
        val dirs = listOf(
            File(context.cacheDir, "anywall_work"),
            File(context.cacheDir, "anywall_payloads")
        )
        return dirs.sumOf { dir ->
            if (dir.exists()) {
                dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else {
                0L
            }
        }
    }

    fun cleanupTempFiles(): Long {
        val beforeBytes = getTempCacheSizeBytes()
        val dirs = listOf(
            File(context.cacheDir, "anywall_work"),
            File(context.cacheDir, "anywall_payloads")
        )
        for (dir in dirs) {
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    try {
                        file.deleteRecursively()
                    } catch (_: Throwable) {
                    }
                }
            }
        }
        return beforeBytes
    }

    private fun sniffMagicHeader(uri: Uri): SupportedFormat? {
        return try {
            val header = ByteArray(16)
            val read = openInputStream(uri).use { it.read(header) }
            if (read < 4) return null

            when {
                header[0] == 0x25.toByte() && header[1] == 0x50.toByte() &&
                    header[2] == 0x44.toByte() && header[3] == 0x46.toByte() -> SupportedFormat.PDF

                header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
                    header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() -> SupportedFormat.PNG

                header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() &&
                    header[2] == 0xFF.toByte() -> SupportedFormat.JPG

                read >= 6 &&
                    header[0] == 0x47.toByte() && header[1] == 0x49.toByte() &&
                    header[2] == 0x46.toByte() && header[3] == 0x38.toByte() -> SupportedFormat.GIF

                read >= 12 &&
                    header[0] == 0x52.toByte() && header[1] == 0x49.toByte() &&
                    header[2] == 0x46.toByte() && header[3] == 0x46.toByte() -> {
                    val tag = String(header, 8, 4, Charsets.US_ASCII)
                    when (tag) {
                        "WEBP" -> SupportedFormat.WEBP
                        "WAVE" -> SupportedFormat.WAV
                        else -> null
                    }
                }

                read >= 12 &&
                    header[4] == 0x66.toByte() && header[5] == 0x74.toByte() &&
                    header[6] == 0x79.toByte() && header[7] == 0x70.toByte() -> {
                    val brand = String(header, 8, 4, Charsets.US_ASCII).lowercase(Locale.ROOT)
                    when {
                        brand.startsWith("hei") || brand.startsWith("mif") || brand.startsWith("msf") -> SupportedFormat.HEIC
                        brand.startsWith("m4a") -> SupportedFormat.M4A
                        brand.startsWith("qt") -> SupportedFormat.MOV
                        else -> SupportedFormat.MP4
                    }
                }

                header[0] == 0x42.toByte() && header[1] == 0x4D.toByte() -> SupportedFormat.BMP

                read >= 6 &&
                    header[0] == 0x37.toByte() && header[1] == 0x7A.toByte() &&
                    header[2] == 0xBC.toByte() && header[3] == 0xAF.toByte() -> SupportedFormat.SEVEN_Z

                read >= 6 &&
                    header[0] == 0xFD.toByte() && header[1] == 0x37.toByte() &&
                    header[2] == 0x7A.toByte() && header[3] == 0x58.toByte() &&
                    header[4] == 0x5A.toByte() && header[5] == 0x00.toByte() -> SupportedFormat.XZ

                header[0] == 0x42.toByte() && header[1] == 0x5A.toByte() &&
                    header[2] == 0x68.toByte() -> SupportedFormat.BZ2

                header[0] == 0x1F.toByte() && header[1] == 0x8B.toByte() -> SupportedFormat.GZ

                header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() && header[3] == 0x04.toByte() -> SupportedFormat.ZIP

                header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
                    header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte() -> SupportedFormat.MKV

                (header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()) ||
                    (header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0) -> SupportedFormat.MP3

                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        fun sanitizeFileName(rawName: String): String {
            val cleaned = rawName
                .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_")
                .trim()
                .trim('.')
            return cleaned.ifBlank { "anywall_${System.currentTimeMillis().toString().takeLast(6)}" }
        }

        fun buildOutputFileName(
            baseName: String,
            extension: String,
            suffix: String = ""
        ): String {
            val sanitized = sanitizeFileName(baseName)
            val cleanBase = sanitized
                .removeSuffix(".tar.gz")
                .removeSuffix(".tar.xz")
                .removeSuffix(".tar.bz2")
                .let { name ->
                    if (name.contains('.')) name.substringBeforeLast('.') else name
                }
                .ifBlank { "anywall_convertido" }
            val cleanExt = extension.trimStart('.').lowercase(Locale.ROOT)
            return if (cleanExt.isEmpty() || cleanExt == "folder") {
                "$cleanBase$suffix"
            } else {
                "$cleanBase$suffix.$cleanExt"
            }
        }
    }
}
