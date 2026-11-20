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
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.Deflater
import kotlin.coroutines.coroutineContext

/**
 * Motor 4: `ArchiveEngineImpl` (Archivos Comprimidos - v0.2)
 * - Empaquetado de uno o múltiples archivos de cualquier tipo en un nuevo `.zip`, `.tar`, `.tar.gz`,
 *   `.tar.xz` o `.tar.bz2` usando `Apache Commons Compress` + `org.tukaani:xz`.
 * - Extracción completa del contenido de archivos `.zip`, `.tar`, `.tar.gz`, `.tar.xz`, `.tar.bz2`,
 *   `.gz`, `.xz`, `.bz2` y `.7z` (usando `SevenZFile` + `org.tukaani:xz`) dentro de una subcarpeta
 *   organizada en `Downloads/AnyWall/` con protección estricta contra Zip-Slip.
 */
class ArchiveEngineImpl(
    private val uriResolver: UriFileResolver,
    private val storageManager: ScopedStorageManager
) : FileConverterEngine {

    private val extractableArchiveFormats = setOf(
        SupportedFormat.ZIP,
        SupportedFormat.TAR,
        SupportedFormat.TAR_GZ,
        SupportedFormat.TAR_XZ,
        SupportedFormat.TAR_BZ2,
        SupportedFormat.GZ,
        SupportedFormat.XZ,
        SupportedFormat.BZ2,
        SupportedFormat.SEVEN_Z
    )

    private val packageOutputFormats = listOf(
        SupportedFormat.ZIP,
        SupportedFormat.TAR,
        SupportedFormat.TAR_GZ,
        SupportedFormat.TAR_XZ,
        SupportedFormat.TAR_BZ2
    )

    override fun canHandle(format: SupportedFormat): Boolean {
        return true
    }

    override fun getSupportedTargetFormats(sourceFormats: List<SupportedFormat>): List<SupportedFormat> {
        if (sourceFormats.isEmpty()) return emptyList()
        val distinct = sourceFormats.distinct()
        val targets = linkedSetOf<SupportedFormat>()

        if (distinct.all { it in extractableArchiveFormats }) {
            targets.add(SupportedFormat.EXTRACT_ARCHIVE)
        }

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

                SupportedFormat.TAR_XZ -> {
                    FileOutputStream(tempArchiveFile).use { fos ->
                        BufferedOutputStream(fos, 64 * 1024).use { bos ->
                            XZCompressorOutputStream(bos).use { xzOut ->
                                TarArchiveOutputStream(xzOut, "UTF-8").use { tarOut ->
                                    tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                                    tarOut.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)

                                    writeInputsToTarStream(inputs, tarOut, ::uniqueEntryName, onProgress)
                                    tarOut.finish()
                                }
                            }
                        }
                    }
                }

                SupportedFormat.TAR_BZ2 -> {
                    FileOutputStream(tempArchiveFile).use { fos ->
                        BufferedOutputStream(fos, 64 * 1024).use { bos ->
                            BZip2CompressorOutputStream(bos).use { bz2Out ->
                                TarArchiveOutputStream(bz2Out, "UTF-8").use { tarOut ->
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
            val totalSourceBytes = inputs.sumOf { it.sizeBytes }
            val sourceLabel = if (inputs.size == 1) {
                first.displayName
            } else {
                "${first.displayName} (+${inputs.size - 1} archivos)"
            }

            return listOf(
                ConversionJobResult(
                    sourceDisplayName = sourceLabel,
                    sourceFormat = first.format,
                    sourceSizeBytes = totalSourceBytes,
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
                SupportedFormat.TAR -> extractTarArchive(item, subFolderName, TarCompression.NONE) { subP, msg ->
                    onProgress((baseProgress + subP * slice).coerceIn(0f, 0.99f), msg)
                }
                SupportedFormat.TAR_GZ -> extractTarArchive(item, subFolderName, TarCompression.GZIP) { subP, msg ->
                    onProgress((baseProgress + subP * slice).coerceIn(0f, 0.99f), msg)
                }
                SupportedFormat.TAR_XZ -> extractTarArchive(item, subFolderName, TarCompression.XZ) { subP, msg ->
                    onProgress((baseProgress + subP * slice).coerceIn(0f, 0.99f), msg)
                }
                SupportedFormat.TAR_BZ2 -> extractTarArchive(item, subFolderName, TarCompression.BZIP2) { subP, msg ->
                    onProgress((baseProgress + subP * slice).coerceIn(0f, 0.99f), msg)
                }
                SupportedFormat.GZ -> extractSingleCompressedStreamArchive(
                    item = item,
                    rootSubFolder = subFolderName,
                    compression = TarCompression.GZIP,
                    suffixesToStrip = listOf(".gz", ".GZ")
                ) { subP, msg ->
                    onProgress((baseProgress + subP * slice).coerceIn(0f, 0.99f), msg)
                }
                SupportedFormat.XZ -> extractSingleCompressedStreamArchive(
                    item = item,
                    rootSubFolder = subFolderName,
                    compression = TarCompression.XZ,
                    suffixesToStrip = listOf(".xz", ".XZ")
                ) { subP, msg ->
                    onProgress((baseProgress + subP * slice).coerceIn(0f, 0.99f), msg)
                }
                SupportedFormat.BZ2 -> extractSingleCompressedStreamArchive(
                    item = item,
                    rootSubFolder = subFolderName,
                    compression = TarCompression.BZIP2,
                    suffixesToStrip = listOf(".bz2", ".BZ2", ".bzip2")
                ) { subP, msg ->
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

    private enum class TarCompression {
        NONE, GZIP, XZ, BZIP2
    }

    private fun wrapWithDecompressor(input: InputStream, compression: TarCompression): InputStream {
        return when (compression) {
            TarCompression.NONE -> input
            TarCompression.GZIP -> GzipCompressorInputStream(input, true)
            TarCompression.XZ -> XZCompressorInputStream(input, true)
            TarCompression.BZIP2 -> BZip2CompressorInputStream(input, true)
        }
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
        compression: TarCompression,
        onSubProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        var count = 0

        uriResolver.openInputStream(item.uri).use { rawStream ->
            BufferedInputStream(rawStream, 64 * 1024).use { bis ->
                wrapWithDecompressor(bis, compression).use { tarSource ->
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

    private suspend fun extractSingleCompressedStreamArchive(
        item: InputFileItem,
        rootSubFolder: String,
        compression: TarCompression,
        suffixesToStrip: List<String>,
        onSubProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val isInternalTar = try {
            uriResolver.openInputStream(item.uri).use { raw ->
                wrapWithDecompressor(BufferedInputStream(raw), compression).use { decIn ->
                    TarArchiveInputStream(decIn).use { tarIn ->
                        val firstEntry = tarIn.nextEntry
                        firstEntry != null && firstEntry.name.isNotBlank()
                    }
                }
            }
        } catch (_: Throwable) {
            false
        }

        if (isInternalTar) {
            return extractTarArchive(item, rootSubFolder, compression, onSubProgress)
        }

        onSubProgress(0.3f, "Descomprimiendo ${item.displayName}…")
        var strippedName = item.displayName
        for (suffix in suffixesToStrip) {
            if (strippedName.endsWith(suffix)) {
                strippedName = strippedName.removeSuffix(suffix)
                break
            }
        }
        val extractedFileName = strippedName.ifBlank { "${item.baseNameWithoutExtension}_extraido" }

        return uriResolver.openInputStream(item.uri).use { rawStream ->
            BufferedInputStream(rawStream, 64 * 1024).use { bis ->
                wrapWithDecompressor(bis, compression).use { decIn ->
                    val savedResult = saveExtractedStreamEntry(
                        sourceArchive = item,
                        rootSubFolder = rootSubFolder,
                        rawEntryName = extractedFileName,
                        entryInputStream = decIn
                    )
                    onSubProgress(1f, "Extraído ${savedResult.outputDisplayName}")
                    listOf(savedResult)
                }
            }
        }
    }

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
                                    sourceSizeBytes = item.sizeBytes,
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
        entryInputStream: InputStream
    ): ConversionJobResult {
        val (nestedSubFolder, cleanFileName) = splitEntryFolderAndName(rootSubFolder, rawEntryName)
        val detectedFormat = SupportedFormat.detect(cleanFileName, null) ?: SupportedFormat.TXT

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
            sourceSizeBytes = sourceArchive.sizeBytes,
            outputDisplayName = saved.displayName,
            outputFormat = detectedFormat,
            outputCategory = FormatCategory.ARCHIVE,
            outputUri = saved.uri,
            outputAbsolutePath = saved.absolutePath,
            outputSizeBytes = saved.sizeBytes,
            outputMimeType = saved.mimeType
        )
    }

    companion object {
        /**
         * Protege contra Zip-Slip eliminando segmentos `..` y separa los subdirectorios internos
         * del nombre final del archivo extraído dentro de `Downloads/AnyWall/<rootSubFolder>/`.
         */
        fun splitEntryFolderAndName(
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
}
