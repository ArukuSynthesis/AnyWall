package com.anywall.converter.core

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
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
 * Resolutor seguro de URIs (`content://` y `file://`) mediante `ContentResolver`.
 * Extrae nombre real, tamaño, tipo MIME, detecta el `SupportedFormat` (incluyendo inspección
 * de cabeceras mágicas como fallback) y gestiona copias temporales en caché cuando un motor
 * nativo requiere acceso aleatorio a archivo (`File` / `ParcelFileDescriptor`).
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
                // Algunos proveedores (como Share Sheet de WhatsApp o Galería) otorgan permisos temporales por Intent
            } catch (_: Throwable) {
            }
        }
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

    /**
     * Abre un `InputStream` de lectura desde cualquier URI soportada.
     */
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

    /**
     * Copia el contenido de la URI a un archivo temporal en el directorio de trabajo privado
     * de la aplicación. Necesario para motores que operan sobre `File` o `RandomAccessFile`.
     */
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

    /**
     * Crea un archivo temporal vacío en el directorio de trabajo privado de la app.
     */
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

    /**
     * Limpia archivos temporales antiguos en caché.
     */
    fun cleanupTempFiles() {
        val dir = File(context.cacheDir, "anywall_work")
        if (dir.exists()) {
            dir.listFiles()?.forEach { file ->
                try {
                    file.deleteRecursively()
                } catch (_: Throwable) {
                }
            }
        }
    }

    /**
     * Inspecciona los primeros bytes (magic header) del archivo cuando la URI no incluye extensión.
     */
    private fun sniffMagicHeader(uri: Uri): SupportedFormat? {
        return try {
            val header = ByteArray(16)
            val read = openInputStream(uri).use { it.read(header) }
            if (read < 4) return null

            when {
                // PDF: %PDF
                header[0] == 0x25.toByte() && header[1] == 0x50.toByte() &&
                    header[2] == 0x44.toByte() && header[3] == 0x46.toByte() -> SupportedFormat.PDF

                // PNG: 89 50 4E 47
                header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
                    header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() -> SupportedFormat.PNG

                // JPG: FF D8 FF
                header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() &&
                    header[2] == 0xFF.toByte() -> SupportedFormat.JPG

                // WEBP / WAV: RIFF....WEBP o RIFF....WAVE
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

                // BMP: BM
                header[0] == 0x42.toByte() && header[1] == 0x4D.toByte() -> SupportedFormat.BMP

                // 7z: 37 7A BC AF 27 1C
                read >= 6 &&
                    header[0] == 0x37.toByte() && header[1] == 0x7A.toByte() &&
                    header[2] == 0xBC.toByte() && header[3] == 0xAF.toByte() -> SupportedFormat.SEVEN_Z

                // GZIP: 1F 8B
                header[0] == 0x1F.toByte() && header[1] == 0x8B.toByte() -> SupportedFormat.GZ

                // ZIP / DOCX: PK\x03\x04
                header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() && header[3] == 0x04.toByte() -> SupportedFormat.ZIP

                // Matroska / WebM: 1A 45 DF A3
                header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
                    header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte() -> SupportedFormat.MKV

                // MP3 ID3 o sync frame
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
            val cleanBase = sanitizeFileName(baseName)
                .removeSuffix(".tar.gz")
                .substringBeforeLast('.', missingDelimiterValue = sanitizeFileName(baseName))
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
