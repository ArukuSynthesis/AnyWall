package com.anywall.converter.core

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Resultado de persistencia de un archivo convertido en `Downloads/AnyWall/`.
 */
data class SavedOutputFile(
    val displayName: String,
    val uri: Uri,
    val absolutePath: String,
    val sizeBytes: Long,
    val mimeType: String
)

/**
 * Gestor de almacenamiento seguro (Scoped Storage - v0.2):
 * - En Android 10+ (API 29+): Guarda los archivos en `Downloads/AnyWall/` usando `MediaStore.Downloads`
 *   con `IS_PENDING` para escritura atómica y segura sin requerir permisos de almacenamiento.
 * - En Android 8.0 - 9.0 (API 26-28): Guarda en el directorio público `Downloads/AnyWall/` y expone
 *   una URI segura mediante `FileProvider`.
 */
class ScopedStorageManager(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Guarda un archivo temporal local dentro de `Downloads/AnyWall/[subFolder]`.
     */
    @Throws(IOException::class)
    fun saveTempFileToDownloads(
        sourceFile: File,
        desiredDisplayName: String,
        mimeType: String,
        subFolder: String? = null
    ): SavedOutputFile {
        if (!sourceFile.exists()) {
            throw IOException("El archivo temporal de origen no existe: ${sourceFile.absolutePath}")
        }
        val cleanName = UriFileResolver.sanitizeFileName(desiredDisplayName)
        val cleanSubFolder = subFolder
            ?.split('/', '\\')
            ?.map { UriFileResolver.sanitizeFileName(it) }
            ?.filter { it.isNotBlank() }
            ?.joinToString("/")
            ?.takeIf { it.isNotBlank() }

        return FileInputStream(sourceFile).use { input ->
            saveStreamToDownloads(
                inputStream = input,
                desiredDisplayName = cleanName,
                mimeType = mimeType,
                subFolder = cleanSubFolder,
                knownSizeBytes = sourceFile.length()
            )
        }
    }

    /**
     * Guarda un flujo de datos directamente en `Downloads/AnyWall/[subFolder]`.
     */
    @Throws(IOException::class)
    fun saveStreamToDownloads(
        inputStream: InputStream,
        desiredDisplayName: String,
        mimeType: String,
        subFolder: String? = null,
        knownSizeBytes: Long = -1L
    ): SavedOutputFile {
        val cleanName = UriFileResolver.sanitizeFileName(desiredDisplayName)
        val cleanSubFolder = subFolder
            ?.split('/', '\\')
            ?.map { UriFileResolver.sanitizeFileName(it) }
            ?.filter { it.isNotBlank() }
            ?.joinToString("/")
            ?.takeIf { it.isNotBlank() }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStoreApi29(
                inputStream = inputStream,
                displayName = cleanName,
                mimeType = mimeType,
                subFolder = cleanSubFolder
            )
        } else {
            saveViaLegacyDownloadsApi26(
                inputStream = inputStream,
                displayName = cleanName,
                mimeType = mimeType,
                subFolder = cleanSubFolder
            )
        }
    }

    @Throws(IOException::class)
    private fun saveViaMediaStoreApi29(
        inputStream: InputStream,
        displayName: String,
        mimeType: String,
        subFolder: String?
    ): SavedOutputFile {
        val relativePath = buildString {
            append(Environment.DIRECTORY_DOWNLOADS)
            append("/").append(ANYWALL_FOLDER_NAME)
            if (!subFolder.isNullOrBlank()) {
                append("/").append(subFolder)
            }
        }

        val uniqueDisplayName = ensureUniqueDisplayNameOnApi29(displayName, relativePath)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueDisplayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val itemUri = contentResolver.insert(collection, values)
            ?: throw IOException("MediaStore no pudo crear la entrada en $relativePath/$uniqueDisplayName")

        var writtenBytes = 0L
        try {
            val outStream: OutputStream = contentResolver.openOutputStream(itemUri, "w")
                ?: throw IOException("No se pudo abrir OutputStream para MediaStore URI: $itemUri")
            outStream.use { output ->
                writtenBytes = inputStream.copyTo(output, bufferSize = 64 * 1024)
                output.flush()
            }

            val completeValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
                if (writtenBytes > 0L) {
                    put(MediaStore.MediaColumns.SIZE, writtenBytes)
                }
            }
            contentResolver.update(itemUri, completeValues, null, null)
        } catch (e: Throwable) {
            try {
                contentResolver.delete(itemUri, null, null)
            } catch (_: Throwable) {
            }
            throw if (e is IOException) e else IOException("Error guardando archivo en MediaStore", e)
        }

        val resolvedDisplayPath = buildPublicDisplayPath(subFolder, uniqueDisplayName)
        return SavedOutputFile(
            displayName = uniqueDisplayName,
            uri = itemUri,
            absolutePath = resolvedDisplayPath,
            sizeBytes = writtenBytes,
            mimeType = mimeType
        )
    }

    private fun ensureUniqueDisplayNameOnApi29(displayName: String, relativePath: String): String {
        val normalizedRel = if (relativePath.endsWith("/")) relativePath else "$relativePath/"
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val exists = try {
            contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                selection,
                arrayOf(displayName, normalizedRel),
                null
            )?.use { it.moveToFirst() } ?: false
        } catch (_: Throwable) {
            false
        }

        if (!exists) return displayName

        val stamp = (System.currentTimeMillis() % 100000).toString()
        return appendUniqueStampToFileName(displayName, stamp)
    }

    @Throws(IOException::class)
    private fun saveViaLegacyDownloadsApi26(
        inputStream: InputStream,
        displayName: String,
        mimeType: String,
        subFolder: String?
    ): SavedOutputFile {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        var targetDir = File(publicDownloads, ANYWALL_FOLDER_NAME)
        if (!subFolder.isNullOrBlank()) {
            targetDir = File(targetDir, subFolder)
        }

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            // Fallback seguro al directorio externo de la app si en API 26-28 el permiso no fue concedido
            val fallbackBase = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            targetDir = File(fallbackBase, ANYWALL_FOLDER_NAME)
            if (!subFolder.isNullOrBlank()) {
                targetDir = File(targetDir, subFolder)
            }
            targetDir.mkdirs()
        }

        var destFile = File(targetDir, displayName)
        if (destFile.exists()) {
            val stamp = (System.currentTimeMillis() % 100000).toString()
            destFile = File(targetDir, appendUniqueStampToFileName(displayName, stamp))
        }

        var writtenBytes = 0L
        FileOutputStream(destFile).use { output ->
            writtenBytes = inputStream.copyTo(output, bufferSize = 64 * 1024)
            output.flush()
        }

        val providerUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            destFile
        )

        return SavedOutputFile(
            displayName = destFile.name,
            uri = providerUri,
            absolutePath = destFile.absolutePath,
            sizeBytes = writtenBytes.coerceAtLeast(destFile.length()),
            mimeType = mimeType
        )
    }

    private fun appendUniqueStampToFileName(displayName: String, stamp: String): String {
        return when {
            displayName.endsWith(".tar.gz", ignoreCase = true) ->
                "${displayName.dropLast(7)}_$stamp.tar.gz"
            displayName.endsWith(".tar.xz", ignoreCase = true) ->
                "${displayName.dropLast(7)}_$stamp.tar.xz"
            displayName.endsWith(".tar.bz2", ignoreCase = true) ->
                "${displayName.dropLast(8)}_$stamp.tar.bz2"
            displayName.contains('.') ->
                "${displayName.substringBeforeLast('.')}_$stamp.${displayName.substringAfterLast('.')}"
            else ->
                "${displayName}_$stamp"
        }
    }

    /**
     * Elimina un archivo previamente generado tanto de MediaStore como del sistema de archivos.
     */
    fun deleteSavedOutput(uriString: String, absolutePath: String): Boolean {
        var deleted = false
        if (uriString.isNotBlank()) {
            try {
                val uri = Uri.parse(uriString)
                if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                    val rows = contentResolver.delete(uri, null, null)
                    if (rows > 0) deleted = true
                }
            } catch (_: Throwable) {
            }
        }
        if (absolutePath.isNotBlank()) {
            try {
                val file = File(absolutePath)
                if (file.exists() && file.delete()) {
                    deleted = true
                }
            } catch (_: Throwable) {
            }
        }
        return deleted
    }

    private fun buildPublicDisplayPath(subFolder: String?, fileName: String): String {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val base = File(downloadsDir, ANYWALL_FOLDER_NAME)
        val folder = if (!subFolder.isNullOrBlank()) File(base, subFolder) else base
        return File(folder, fileName).absolutePath
    }

    companion object {
        const val ANYWALL_FOLDER_NAME = "AnyWall"
    }
}
