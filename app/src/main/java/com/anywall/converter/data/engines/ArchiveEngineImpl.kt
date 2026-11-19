package com.anywall.converter.data.engines

import com.anywall.converter.core.ScopedStorageManager
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.domain.engine.FileConverterEngine
import com.anywall.converter.domain.model.ConversionJobResult
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.SupportedFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.Deflater
import kotlin.coroutines.coroutineContext

/**
 * Motor 4: `ArchiveEngineImpl` (Archivos Comprimidos)
 * - Empaquetado de uno o múltiples archivos de cualquier tipo en un nuevo `.zip`, `.tar` o `.tar.gz`
 *   usando `Apache Commons Compress`.
 * - Extracción completa del contenido de archivos `.zip`, `.tar`, `.tar.gz`, `.gz` y `.7z`
 *   (usando `SevenZFile` + `org.tukaani:xz`) dentro de una subcarpeta organizada en `Downloads/AnyWall/`.
 */
class ArchiveEngineImpl(
    private val uriResolver: UriFileResolver,
    private val storageManager: ScopedStorageManager
) : FileConverterEngine {

    private val extractableArchiveFormats = setOf(
        SupportedFormat.ZIP,
        SupportedFormat.TAR,
        SupportedFormat.TAR_GZ,
        SupportedFormat.GZ,
        SupportedFormat.SEVEN_Z
    )

    private val packageOutputFormats = listOf(
        SupportedFormat.ZIP,
        SupportedFormat.TAR,
        SupportedFormat.TAR_GZ
    )

    override fun canHandle(format: SupportedFormat): Boolean {
        // ArchiveEngine puede extraer formatos comprimidos y también empaquetar cualquier archivo en ZIP/TAR/TAR.GZ
        return true
    }

    override fun getSupportedTargetFormats(sourceFormats: List<SupportedFormat>): List<SupportedFormat> {
        if (sourceFormats.isEmpty()) return emptyList()
        val distinct = sourceFormats.distinct()
        val targets = linkedSetOf<SupportedFormat>()

        // Si todos los archivos seleccionados son comprimidos, habilitar la acción de extracción completa
        if (distinct.all { it in extractableArchiveFormats }) {
            targets.add(SupportedFormat.EXTRACT_ARCHIVE)
        }

        // Cualquier conjunto de uno o múltiples archivos puede empaquetarse en ZIP, TAR o TAR.GZ
        targets.addAll(packageOutputFormats)
        return targets.toList()
    }

    override suspend fun convert(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> = withContext(Dispatchers.IO) {
        require(inputs.isNotEmpty()) { "No se seleccionaron archivos para procesar en ArchiveEngineImpl." }

        if (targetFormat == SupportedFormat.EXTRACT_ARCHIVE) {
            return@withContext extractAllArchives(inputs, options, onProgress)
        }

        if (targetFormat in packageOutputFormats) {
            return@withContext packageFilesIntoArchive(inputs, targetFormat, options, onProgress)
        }

        throw IllegalArgumentException("Formato destino no soportado por ArchiveEngineImpl: $targetFormat")
    }

    /**
     * Empaqueta todos los archivos de entrada en un único archivo `.zip`, `.tar` o `.tar.gz`.
     */
    private suspend fun packageFilesIntoArchive(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val tempArchiveFile = uriResolver.createTempWorkingFile(
            prefix = "pkg_",
            extension = targetFormat.extension
        )

        val usedEntryNames = mutableSetOf<String>()
        fun uniqueEntryName(original: String): String {
            val clean = UriFileResolver.sanitizeFileName(original)
            if (usedEntryNames.add(clean)) return clean
            val base = clean.substringBeforeLast('.', clean)
            val ext = clean.substringAfterLast('.', "")
            var counter = 2
            while (true) {
                val candidate = if (ext.isNotEmpty()) "${base}_$counter.$ext" else "${base}_$counter"
                if (usedEntryNames.add(candidate)) return candidate
                counter++
            }
        }

        try {
            when (targetFormat) {
                SupportedFormat.ZIP -> {
                    val compressionLevel = when {
                        options.clampedQuality >= 90 -> Deflater.BEST_COMPRESSION
                        options.clampedQuality >= 50 -> Deflater.DEFAULT_COMPRESSION
                        else -> Deflater.BEST_SPEED
                    }
                    FileOutputStream(tempArchiveFile).use { fos ->
                        BufferedOutputStream(fos, 64 * 1024).use { bos ->
                            ZipArchiveOutputStream(bos).use { zipOut ->
                                zipOut.setEncoding("UTF-8")
                                zipOut.setLevel(compressionLevel)

                                inputs.forEachIndexed { index, item ->
                                    coroutineContext.ensureActive()
                                    val p = index.toFloat() / inputs.size.toFloat()
                                    onProgress(
                                        p,
                                        "Empaquetando en ZIP (${index + 1}/${inputs.size}): ${item.displayName}"
                                    )

                                    val entryName = uniqueEntryName(item.displayName)
                                    val entry = ZipArchiveEntry(entryName).apply {
                                        time = System.currentTimeMillis()
                                        if (item.sizeBytes > 0L) {
                                            size = item.sizeBytes
                                        }
                                    }
                                    zipOut.putArchiveEntry(entry)
                                    uriResolver.openInputStream(item.uri).use { inputStream ->
                                        inputStream.copyTo(zipOut, bufferSize = 64 * 1024)
                                    }
                                    zipOut.closeArchiveEntry()
                                }
                                zipOut.finish()
                            }
                        }
                    }
                }

                SupportedFormat.TAR -> {
                    FileOutputStream(tempArchiveFile).use { fos ->
                        BufferedOutputStream(fos, 64 * 1024).use { bos ->
                            TarArchiveOutputStream(bos, "UTF-8").use { tarOut ->
                                tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                                tarOut.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)

                                writeInputsToTarStream(inputs, tarOut, ::uniqueEntryName, onProgress)
                                tarOut.finish()
                            }
                        }
                    }
                }

                SupportedFormat.TAR_GZ -> {
                    FileOutputStream(tempArchiveFile).use { fos ->
                        BufferedOutputStream(fos, 64 * 1024).use { bos ->
                            GzipCompressorOutputStream(bos).use { gzipOut ->
                                TarArchiveOutputStream(gzipOut, "UTF-8").use { tarOut ->
                                    tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                                    tarOut.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)

                                    writeInputsToTarStream(inputs, tarOut, ::uniqueEntryName, onProgress)
                                    tarOut.finish()
                                }
                            }
                        }
                    }
                }

                else -> throw IllegalArgumentException("Formato de empaquetado inválido: $targetFormat")
            }

            val baseName = when {
                options.customOutputBaseName.isNotBlank() -> options.customOutputBaseName
                inputs.size == 1 -> inputs.first().baseNameWithoutExtension
                else -> "${inputs.first().baseNameWithoutExtension}_lote_${inputs.size}"
            }
            val desiredFileName = UriFileResolver.buildOutputFileName(
                baseName = baseName,
                extension = targetFormat.extension
            )

            onProgress(0.95f, "Guardando archivo comprimido $desiredFileName…")
            val saved = storageManager.saveTempFileToDownloads(
                sourceFile = tempArchiveFile,
                desiredDisplayName = desiredFileName,
                mimeType = targetFormat.mimeType
            )

            onProgress(1f, "Archivo comprimido generado: ${saved.displayName}")
            val first = inputs.first()
            val sourceLabel = if (inputs.size == 1) {
                first.displayName
            } else {
                "${first.displayName} (+${inputs.size - 1} archivos)"
            }

            return listOf(
                ConversionJobResult(
                    sourceDisplayName = sourceLabel,
                    sourceFormat = first.format,
                    outputDisplayName = saved.displayName,
                    outputFormat = targetFormat,
                    outputCategory = FormatCategory.ARCHIVE,
                    outputUri = saved.uri,
                    outputAbsolutePath = saved.absolutePath,
                    outputSizeBytes = saved.sizeBytes,
                    outputMimeType = saved.mimeType
                )
            )
        } finally {
            tempArchiveFile.delete()
        }
    }

    private suspend fun writeInputsToTarStream(
        inputs: List<InputFileItem>,
        tarOut: TarArchiveOutputStream,
        uniqueEntryName: (String) -> String,
        onProgress: (Float, String) -> Unit
    ) {
        inputs.forEachIndexed { index, item ->
            coroutineContext.ensureActive()
            val p = index.toFloat() / inputs.size.toFloat()
            onProgress(
                p,
                "Empaquetando en TAR (${index + 1}/${inputs.size}): ${item.displayName}"
            )

            // TarArchiveEntry requiere conocer el tamaño exacto antes de putArchiveEntry
            val tempCopy = uriResolver.copyUriToTempFile(item.uri, prefix = "tar_entry_", extension = "bin")
            try {
                val entryName = uniqueEntryName(item.displayName)
                val entry = TarArchiveEntry(tempCopy, entryName).apply {
                    size = tempCopy.length()
                    modTime = java.util.Date(System.currentTimeMillis())
                }
                tarOut.putArchiveEntry(entry)
                FileInputStream(tempCopy).use { fis ->
                    fis.copyTo(tarOut, bufferSize = 64 * 1024)
                }
                tarOut.closeArchiveEntry()
            } finally {
                tempCopy.delete()
            }
        }
    }

    /**
     * Extrae el contenido completo de uno o múltiples archivos `.zip`, `.tar`, `.tar.gz`, `.gz` o `.7z`
     * dentro de una subcarpeta organizada en `Downloads/AnyWall/<nombre_archivo>/`.
     */
    private suspend fun extractAllArchives(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val allResults = mutableListOf<ConversionJobResult>()
        val totalArchives = inputs.size

        inputs.forEachIndexed { archiveIdx, item ->
            coroutineContext.ensureActive()
            val baseProgress = archiveIdx.toFloat() / totalArchives.toFloat()
            val slice = 1f / totalArchives.toFloat()

            val subFolderName = if (options.customOutputBaseName.isNotBlank() && totalArchives == 1) {
                UriFileResolver.sanitizeFileName(options.customOutputBaseName)
            } else {
                UriFileResolver.sanitizeFileName(item.baseNameWithoutExtension)
            }.ifBlank { "extraido_${System.currentTimeMillis() % 10000}" }

            onProgress(baseProgress, "Extrayendo ${item.displayName} en Downloads/AnyWall/$subFolderName/…")

            val extractedForArchive = when (item.format) {
                SupportedFormat.ZIP -> extractZipArchive(item, subFolderName) { subP, msg ->
                    onProgress((baseProgress + subP * slice).coerceIn(0f, 0.99f), msg)
                }
                SupportedFormat.TAR -> extractTarArchive(item, subFolderName, isGzipped = false) { subP, msg ->
                    onProgress((baseProgress + subP * slice).coerceIn(0f, 0.99f), msg)
                }
                SupportedFormat.TAR_GZ -> extractTarArchive(item, subFolderName, isGzipped = true) { subP, msg ->
                    onProgress((baseProgress + subP * slice).coerceIn(0f, 0.99f), msg)
                }
                SupportedFormat.GZ -> extractGzipArchive(item, subFolderName) { subP, msg ->
                    onProgress((baseProgress + subP * slice).coerceIn(0f, 0.99f), msg)
                }
                SupportedFormat.SEVEN_Z -> extractSevenZArchive(item, subFolderName) { subP, msg ->
                    onProgress((baseProgress + subP * slice).coerceIn(0f, 0.99f), msg)
                }
                else -> throw IllegalArgumentException("Formato no soportado para extracción: ${item.format}")
            }

            allResults.addAll(extractedForArchive)
        }

        onProgress(1f, "Extracción completada (${allResults.size} archivos extraídos).")
        return allResults
    }

    private suspend fun extractZipArchive(
        item: InputFileItem,
        rootSubFolder: String,
        onSubProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        var count = 0

        uriResolver.openInputStream(item.uri).use { rawStream ->
            BufferedInputStream(rawStream, 64 * 1024).use { bis ->
                ZipArchiveInputStream(bis, "UTF-8", true, true).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        coroutineContext.ensureActive()
                        if (!entry.isDirectory) {
                            count++
                            onSubProgress(
                                (count.toFloat() / (count + 4).toFloat()).coerceIn(0.05f, 0.95f),
                                "Extrayendo ${entry.name}…"
                            )
                            val savedResult = saveExtractedStreamEntry(
                                sourceArchive = item,
                                rootSubFolder = rootSubFolder,
                                rawEntryName = entry.name,
                                entryInputStream = zipIn
                            )
                            results.add(savedResult)
                        }
                        entry = zipIn.nextEntry
                    }
                }
            }
        }

        if (results.isEmpty()) {
            throw IOException("El archivo ZIP '${item.displayName}' no contiene archivos extraíbles.")
        }
        return results
    }

    private suspend fun extractTarArchive(
        item: InputFileItem,
        rootSubFolder: String,
        isGzipped: Boolean,
        onSubProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        var count = 0

        uriResolver.openInputStream(item.uri).use { rawStream ->
            BufferedInputStream(rawStream, 64 * 1024).use { bis ->
                val decompressedStream = if (isGzipped) GzipCompressorInputStream(bis, true) else bis
                decompressedStream.use { tarSource ->
                    TarArchiveInputStream(tarSource, "UTF-8").use { tarIn ->
                        var entry = tarIn.nextEntry
                        while (entry != null) {
                            coroutineContext.ensureActive()
                            if (!entry.isDirectory) {
                                count++
                                onSubProgress(
                                    (count.toFloat() / (count + 4).toFloat()).coerceIn(0.05f, 0.95f),
                                    "Extrayendo ${entry.name}…"
                                )
                                val savedResult = saveExtractedStreamEntry(
                                    sourceArchive = item,
                                    rootSubFolder = rootSubFolder,
                                    rawEntryName = entry.name,
                                    entryInputStream = tarIn
                                )
                                results.add(savedResult)
                            }
                            entry = tarIn.nextEntry
                        }
                    }
                }
            }
        }

        if (results.isEmpty()) {
            throw IOException("El archivo TAR '${item.displayName}' no contiene archivos extraíbles.")
        }
        return results
    }

    private suspend fun extractGzipArchive(
        item: InputFileItem,
        rootSubFolder: String,
        onSubProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        // Verificar primero si el .gz contiene internamente un .tar (.tar.gz con extensión .gz)
        val isInternalTar = try {
            uriResolver.openInputStream(item.uri).use { raw ->
                GzipCompressorInputStream(BufferedInputStream(raw)).use { gzIn ->
                    TarArchiveInputStream(gzIn).use { tarIn ->
                        val firstEntry = tarIn.nextEntry
                        firstEntry != null && firstEntry.name.isNotBlank()
                    }
                }
            }
        } catch (_: Throwable) {
            false
        }

        if (isInternalTar) {
            return extractTarArchive(item, rootSubFolder, isGzipped = true, onSubProgress = onSubProgress)
        }

        onSubProgress(0.3f, "Descomprimiendo ${item.displayName}…")
        val extractedFileName = item.displayName
            .removeSuffix(".gz")
            .removeSuffix(".GZ")
            .ifBlank { "${item.baseNameWithoutExtension}_extraido" }

        return uriResolver.openInputStream(item.uri).use { rawStream ->
            BufferedInputStream(rawStream, 64 * 1024).use { bis ->
                GzipCompressorInputStream(bis, true).use { gzipIn ->
                    val savedResult = saveExtractedStreamEntry(
                        sourceArchive = item,
                        rootSubFolder = rootSubFolder,
                        rawEntryName = extractedFileName,
                        entryInputStream = gzipIn
                    )
                    onSubProgress(1f, "Extraído ${savedResult.outputDisplayName}")
                    listOf(savedResult)
                }
            }
        }
    }

    /**
     * Extrae archivos `.7z` usando `SevenZFile` de Apache Commons Compress junto con `org.tukaani:xz` (LZMA/LZMA2).
     */
    private suspend fun extractSevenZArchive(
        item: InputFileItem,
        rootSubFolder: String,
        onSubProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val temp7z = uriResolver.copyUriToTempFile(item.uri, prefix = "archive_7z_", extension = "7z")
        val results = mutableListOf<ConversionJobResult>()

        try {
            SevenZFile.builder().setFile(temp7z).get().use { sevenZFile ->
                val entriesCount = sevenZFile.entries.count { !it.isDirectory }.coerceAtLeast(1)
                var currentIdx = 0
                val buffer = ByteArray(64 * 1024)

                var entry: SevenZArchiveEntry? = sevenZFile.nextEntry
                while (entry != null) {
                    coroutineContext.ensureActive()
                    if (!entry.isDirectory) {
                        currentIdx++
                        val p = (currentIdx.toFloat() / entriesCount.toFloat()).coerceIn(0f, 0.98f)
                        val entryName = entry.name ?: "archivo_$currentIdx"
                        onSubProgress(p, "Extrayendo 7Z ($currentIdx/$entriesCount): $entryName")

                        val (nestedSubFolder, cleanFileName) = splitEntryFolderAndName(
                            rootSubFolder = rootSubFolder,
                            rawEntryName = entryName
                        )
                        val detectedFormat = SupportedFormat.detect(cleanFileName, null) ?: SupportedFormat.TXT
                        val tempEntryFile = uriResolver.createTempWorkingFile(
                            prefix = "7z_entry_",
                            extension = detectedFormat.extension
                        )
                        try {
                            FileOutputStream(tempEntryFile).use { fos ->
                                BufferedOutputStream(fos, 64 * 1024).use { bos ->
                                    while (true) {
                                        val read = sevenZFile.read(buffer, 0, buffer.size)
                                        if (read <= 0) break
                                        bos.write(buffer, 0, read)
                                    }
                                    bos.flush()
                                }
                            }

                            val saved = storageManager.saveTempFileToDownloads(
                                sourceFile = tempEntryFile,
                                desiredDisplayName = cleanFileName,
                                mimeType = detectedFormat.mimeType,
                                subFolder = nestedSubFolder
                            )

                            results.add(
                                ConversionJobResult(
                                    sourceDisplayName = item.displayName,
                                    sourceFormat = item.format,
                                    outputDisplayName = saved.displayName,
                                    outputFormat = detectedFormat,
                                    outputCategory = FormatCategory.ARCHIVE,
                                    outputUri = saved.uri,
                                    outputAbsolutePath = saved.absolutePath,
                                    outputSizeBytes = saved.sizeBytes,
                                    outputMimeType = saved.mimeType
                                )
                            )
                        } finally {
                            tempEntryFile.delete()
                        }
                    }
                    entry = sevenZFile.nextEntry
                }
            }
        } finally {
            temp7z.delete()
        }

        if (results.isEmpty()) {
            throw IOException("El archivo 7Z '${item.displayName}' no contiene archivos extraíbles.")
        }
        return results
    }

    private fun saveExtractedStreamEntry(
        sourceArchive: InputFileItem,
        rootSubFolder: String,
        rawEntryName: String,
        entryInputStream: java.io.InputStream
    ): ConversionJobResult {
        val (nestedSubFolder, cleanFileName) = splitEntryFolderAndName(rootSubFolder, rawEntryName)
        val detectedFormat = SupportedFormat.detect(cleanFileName, null) ?: SupportedFormat.TXT

        // No cerramos entryInputStream aquí porque pertenece al ArchiveInputStream padre
        val nonClosingStream = object : java.io.FilterInputStream(entryInputStream) {
            override fun close() {
                // No-op: el bucle principal cierra el ArchiveInputStream
            }
        }

        val saved = storageManager.saveStreamToDownloads(
            inputStream = nonClosingStream,
            desiredDisplayName = cleanFileName,
            mimeType = detectedFormat.mimeType,
            subFolder = nestedSubFolder
        )

        return ConversionJobResult(
            sourceDisplayName = sourceArchive.displayName,
            sourceFormat = sourceArchive.format,
            outputDisplayName = saved.displayName,
            outputFormat = detectedFormat,
            outputCategory = FormatCategory.ARCHIVE,
            outputUri = saved.uri,
            outputAbsolutePath = saved.absolutePath,
            outputSizeBytes = saved.sizeBytes,
            outputMimeType = saved.mimeType
        )
    }

    /**
     * Protege contra Zip-Slip eliminando segmentos `..` y separa los subdirectorios internos
     * del nombre final del archivo extraído dentro de `Downloads/AnyWall/<rootSubFolder>/`.
     */
    private fun splitEntryFolderAndName(
        rootSubFolder: String,
        rawEntryName: String
    ): Pair<String, String> {
        val segments = rawEntryName
            .replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .map { UriFileResolver.sanitizeFileName(it) }

        if (segments.isEmpty()) {
            return rootSubFolder to "archivo_extraido"
        }

        val fileName = segments.last()
        val parentSegments = segments.dropLast(1)
        val fullSubFolder = if (parentSegments.isEmpty()) {
            rootSubFolder
        } else {
            "$rootSubFolder/${parentSegments.joinToString("/")}"
        }
        return fullSubFolder to fileName
    }
}
