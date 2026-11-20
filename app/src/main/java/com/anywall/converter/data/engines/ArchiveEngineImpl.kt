package com.anywall.converter.data.engines

import com.anywall.converter.core.ScopedStorageManager
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.domain.engine.FileConverterEngine
import com.anywall.converter.domain.model.ConversionJobResult
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.OutputNamingTemplateResolver
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
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * Entrada individual inspeccionada dentro de un archivo comprimido para `ArchivePreviewDialog` (v0.3).
 */
data class ArchiveEntryPreview(
    val entryPath: String,
    val sizeBytes: Long,
    val isDirectory: Boolean
)

/**
 * Motor 4: `ArchiveEngineImpl` (Archivos Comprimidos, Extracción Selectiva y File Splitter/Joiner - v0.3)
 * - Empaquetado en `.zip`, `.tar`, `.tar.gz`, `.tar.xz` o `.tar.bz2` con selector de nivel de compresión
 *   (`FAST`, `BALANCED`, `MAXIMUM`) y preservación opcional de subcarpetas (`relativeFolderPath`).
 * - Inspección previa de entradas (`inspectArchiveEntries`) y extracción selectiva (`selectedArchiveEntries`)
 *   de `.zip`, `.tar`, `.tar.gz`, `.tar.xz`, `.tar.bz2`, `.gz`, `.xz`, `.bz2` y `.7z` con protección anti-Zip-Slip.
 * - División de archivos grandes en partes `.part001`, `.part002`... + manifiesto `SHA-256` (`FILE_PARTS`)
 *   y reconstrucción verificada (`JOIN_PARTS`).
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

        if (distinct.any { it == SupportedFormat.FILE_PARTS }) {
            targets.add(SupportedFormat.JOIN_PARTS)
        }

        if (distinct.all { it in extractableArchiveFormats }) {
            targets.add(SupportedFormat.EXTRACT_ARCHIVE)
        }

        targets.addAll(packageOutputFormats)
        targets.add(SupportedFormat.FILE_PARTS)
        return targets.toList()
    }

    override suspend fun convert(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> = withContext(Dispatchers.IO) {
        require(inputs.isNotEmpty()) { "No se seleccionaron archivos para procesar en ArchiveEngineImpl." }

        if (targetFormat == SupportedFormat.JOIN_PARTS) {
            return@withContext joinSplitPartFiles(inputs, options, onProgress)
        }

        if (targetFormat == SupportedFormat.FILE_PARTS) {
            return@withContext splitInputFilesIntoParts(inputs, options, onProgress)
        }

        if (targetFormat == SupportedFormat.EXTRACT_ARCHIVE) {
            return@withContext extractAllArchives(inputs, options, onProgress)
        }

        if (targetFormat in packageOutputFormats) {
            return@withContext packageFilesIntoArchive(inputs, targetFormat, options, onProgress)
        }

        throw IllegalArgumentException("Formato destino no soportado por ArchiveEngineImpl: $targetFormat")
    }

    /**
     * Inspecciona el contenido de un archivo comprimido sin extraerlo a almacenamiento permanente,
     * permitiendo al usuario previsualizar y elegir qué entradas extraer en `ArchivePreviewDialog`.
     */
    fun inspectArchiveEntries(item: InputFileItem, maxEntries: Int = 500): List<ArchiveEntryPreview> {
        val list = mutableListOf<ArchiveEntryPreview>()
        try {
            when (item.format) {
                SupportedFormat.ZIP -> {
                    uriResolver.openInputStream(item.uri).use { raw ->
                        ZipArchiveInputStream(BufferedInputStream(raw, 32 * 1024), "UTF-8", true, true).use { zipIn ->
                            var entry = zipIn.nextEntry
                            while (entry != null && list.size < maxEntries) {
                                if (!entry.isDirectory) {
                                    list.add(
                                        ArchiveEntryPreview(
                                            entryPath = entry.name,
                                            sizeBytes = entry.size.coerceAtLeast(0L),
                                            isDirectory = false
                                        )
                                    )
                                }
                                entry = zipIn.nextEntry
                            }
                        }
                    }
                }
                SupportedFormat.TAR,
                SupportedFormat.TAR_GZ,
                SupportedFormat.TAR_XZ,
                SupportedFormat.TAR_BZ2 -> {
                    val comp = when (item.format) {
                        SupportedFormat.TAR_GZ -> TarCompression.GZIP
                        SupportedFormat.TAR_XZ -> TarCompression.XZ
                        SupportedFormat.TAR_BZ2 -> TarCompression.BZIP2
                        else -> TarCompression.NONE
                    }
                    uriResolver.openInputStream(item.uri).use { raw ->
                        wrapWithDecompressor(BufferedInputStream(raw, 32 * 1024), comp).use { dec ->
                            TarArchiveInputStream(dec, "UTF-8").use { tarIn ->
                                var entry = tarIn.nextEntry
                                while (entry != null && list.size < maxEntries) {
                                    if (!entry.isDirectory) {
                                        list.add(
                                            ArchiveEntryPreview(
                                                entryPath = entry.name,
                                                sizeBytes = entry.size.coerceAtLeast(0L),
                                                isDirectory = false
                                            )
                                        )
                                    }
                                    entry = tarIn.nextEntry
                                }
                            }
                        }
                    }
                }
                SupportedFormat.SEVEN_Z -> {
                    val temp7z = uriResolver.copyUriToTempFile(item.uri, "inspect_7z_", "7z")
                    try {
                        SevenZFile.builder().setFile(temp7z).get().use { sevenZ ->
                            for (entry in sevenZ.entries) {
                                if (!entry.isDirectory && list.size < maxEntries) {
                                    list.add(
                                        ArchiveEntryPreview(
                                            entryPath = entry.name ?: "archivo",
                                            sizeBytes = entry.size.coerceAtLeast(0L),
                                            isDirectory = false
                                        )
                                    )
                                }
                            }
                        }
                    } finally {
                        temp7z.delete()
                    }
                }
                else -> {
                    list.add(
                        ArchiveEntryPreview(
                            entryPath = item.baseNameWithoutExtension,
                            sizeBytes = item.sizeBytes,
                            isDirectory = false
                        )
                    )
                }
            }
        } catch (_: Throwable) {
        }
        return list
    }

    private suspend fun splitInputFilesIntoParts(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        val partSizeBytes = options.fileSplitPartSizeMb.coerceIn(1, 500).toLong() * 1024L * 1024L
        val total = inputs.size

        inputs.forEachIndexed { idx, item ->
            coroutineContext.ensureActive()
            onProgress(
                idx.toFloat() / total.toFloat(),
                "Dividiendo ${item.displayName} en partes de ${options.fileSplitPartSizeMb} MB…"
            )
            val tempSrc = uriResolver.copyUriToTempFile(item.uri, "split_in_", item.format.extension)
            val workDir = File(uriResolver.getWorkingTempDir(), "parts_${System.currentTimeMillis()}")
            try {
                val splitRes = FileSplitterAndJoiner.splitFileIntoParts(
                    sourceFile = tempSrc,
                    originalFileName = item.displayName,
                    outputDir = workDir,
                    maxPartSizeBytes = partSizeBytes
                )
                val subFolder = "${item.baseNameWithoutExtension}_partes"
                val approxSourcePerPart = (item.sizeBytes / splitRes.partFiles.size.coerceAtLeast(1)).coerceAtLeast(1L)

                for (partFile in splitRes.partFiles) {
                    val savedPart = storageManager.saveTempFileToDownloads(
                        sourceFile = partFile,
                        desiredDisplayName = partFile.name,
                        mimeType = SupportedFormat.FILE_PARTS.mimeType,
                        subFolder = subFolder
                    )
                    results.add(
                        ConversionJobResult(
                            sourceDisplayName = item.displayName,
                            sourceFormat = item.format,
                            sourceSizeBytes = approxSourcePerPart,
                            outputDisplayName = savedPart.displayName,
                            outputFormat = SupportedFormat.FILE_PARTS,
                            outputCategory = FormatCategory.ARCHIVE,
                            outputUri = savedPart.uri,
                            outputAbsolutePath = savedPart.absolutePath,
                            outputSizeBytes = savedPart.sizeBytes,
                            outputMimeType = savedPart.mimeType
                        )
                    )
                }

                // Guardar también el manifiesto SHA-256 verificable
                val savedManifest = storageManager.saveTempFileToDownloads(
                    sourceFile = splitRes.manifestFile,
                    desiredDisplayName = splitRes.manifestFile.name,
                    mimeType = "text/plain",
                    subFolder = subFolder
                )
                results.add(
                    ConversionJobResult(
                        sourceDisplayName = "${item.displayName} (Manifiesto SHA-256)",
                        sourceFormat = item.format,
                        sourceSizeBytes = savedManifest.sizeBytes,
                        outputDisplayName = savedManifest.displayName,
                        outputFormat = SupportedFormat.TXT,
                        outputCategory = FormatCategory.ARCHIVE,
                        outputUri = savedManifest.uri,
                        outputAbsolutePath = savedManifest.absolutePath,
                        outputSizeBytes = savedManifest.sizeBytes,
                        outputMimeType = savedManifest.mimeType
                    )
                )
            } finally {
                tempSrc.delete()
                workDir.deleteRecursively()
            }
        }
        onProgress(1f, "Archivo dividido en partes con manifiesto SHA-256.")
        return results
    }

    private suspend fun joinSplitPartFiles(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val manifestItem = inputs.firstOrNull {
            it.displayName.endsWith(".anywall-parts.txt", ignoreCase = true)
        }
        val expectedSha256 = manifestItem?.let { mItem ->
            val manifestText = uriResolver.openInputStream(mItem.uri).use {
                it.bufferedReader(Charsets.UTF_8).readText()
            }
            FileSplitterAndJoiner.parseExpectedSha256FromManifest(manifestText)
        }

        val partItems = inputs.filter { it != manifestItem }
        require(partItems.isNotEmpty()) { "Selecciona los archivos .part001, .part002... para reconstruir." }

        val tempParts = mutableListOf<File>()
        val tempJoined = uriResolver.createTempWorkingFile("joined_", "bin")
        try {
            partItems.forEachIndexed { idx, item ->
                coroutineContext.ensureActive()
                onProgress(
                    (idx.toFloat() / partItems.size.toFloat()) * 0.6f,
                    "Preparando parte ${idx + 1} de ${partItems.size}: ${item.displayName}"
                )
                val destPart = File(uriResolver.getWorkingTempDir(), item.displayName)
                uriResolver.openInputStream(item.uri).use { input ->
                    FileOutputStream(destPart).use { output ->
                        input.copyTo(output, 64 * 1024)
                    }
                }
                tempParts.add(destPart)
            }

            onProgress(0.75f, "Reconstruyendo archivo original y verificando integridad SHA-256…")
            val joinResult = FileSplitterAndJoiner.joinPartFiles(
                partFiles = tempParts,
                outputFile = tempJoined,
                expectedSha256Hex = expectedSha256
            )

            val desiredName = if (options.customOutputBaseName.isNotBlank()) {
                val ext = joinResult.originalFileName.substringAfterLast('.', "bin")
                UriFileResolver.buildOutputFileName(options.customOutputBaseName, ext)
            } else {
                joinResult.originalFileName
            }
            val detectedFmt = SupportedFormat.detect(desiredName, null) ?: SupportedFormat.ZIP

            val saved = storageManager.saveTempFileToDownloads(
                sourceFile = tempJoined,
                desiredDisplayName = desiredName,
                mimeType = detectedFmt.mimeType
            )

            onProgress(1f, "Archivo reconstruido y verificado: ${saved.displayName}")
            return listOf(
                ConversionJobResult(
                    sourceDisplayName = "${partItems.first().displayName} (+${partItems.size - 1} partes)",
                    sourceFormat = SupportedFormat.FILE_PARTS,
                    sourceSizeBytes = partItems.sumOf { it.sizeBytes },
                    outputDisplayName = saved.displayName,
                    outputFormat = detectedFmt,
                    outputCategory = detectedFmt.category,
                    outputUri = saved.uri,
                    outputAbsolutePath = saved.absolutePath,
                    outputSizeBytes = saved.sizeBytes,
                    outputMimeType = saved.mimeType
                )
            )
        } finally {
            tempParts.forEach { it.delete() }
            tempJoined.delete()
        }
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
        fun uniqueEntryName(item: InputFileItem): String {
            val cleanFile = UriFileResolver.sanitizeFileName(item.displayName)
            val prefix = if (options.preserveSubfolderStructure && item.relativeFolderPath.isNotBlank()) {
                item.relativeFolderPath
                    .split('/', '\\')
                    .map { UriFileResolver.sanitizeFileName(it) }
                    .filter { it.isNotBlank() }
                    .joinToString("/") + "/"
            } else {
                ""
            }
            val fullCandidate = "$prefix$cleanFile"
            if (usedEntryNames.add(fullCandidate)) return fullCandidate
            val base = cleanFile.substringBeforeLast('.', cleanFile)
            val ext = cleanFile.substringAfterLast('.', "")
            var counter = 2
            while (true) {
                val candidate = if (ext.isNotEmpty()) "$prefix${base}_$counter.$ext" else "$prefix${base}_$counter"
                if (usedEntryNames.add(candidate)) return candidate
                counter++
            }
        }

        val compLevel = options.archiveCompressionLevel

        try {
            when (targetFormat) {
                SupportedFormat.ZIP -> {
                    FileOutputStream(tempArchiveFile).use { fos ->
                        BufferedOutputStream(fos, 64 * 1024).use { bos ->
                            ZipArchiveOutputStream(bos).use { zipOut ->
                                zipOut.setEncoding("UTF-8")
                                zipOut.setLevel(compLevel.zipLevel)

                                inputs.forEachIndexed { index, item ->
                                    coroutineContext.ensureActive()
                                    val p = index.toFloat() / inputs.size.toFloat()
                                    onProgress(
                                        p,
                                        "Empaquetando en ZIP (${index + 1}/${inputs.size}): ${item.displayName}"
                                    )

                                    val entryName = uniqueEntryName(item)
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
                    val gzParams = GzipParameters().apply {
                        compressionLevel = compLevel.zipLevel
                    }
                    FileOutputStream(tempArchiveFile).use { fos ->
                        BufferedOutputStream(fos, 64 * 1024).use { bos ->
                            GzipCompressorOutputStream(bos, gzParams).use { gzipOut ->
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
                            XZCompressorOutputStream(bos, compLevel.xzPreset).use { xzOut ->
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
                            BZip2CompressorOutputStream(bos, compLevel.zipLevel.coerceIn(1, 9)).use { bz2Out ->
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

            val first = inputs.first()
            val baseName = when {
                options.customOutputBaseName.isNotBlank() -> OutputNamingTemplateResolver.resolveBaseName(
                    templateOrCustomName = options.customOutputBaseName,
                    originalBaseName = first.baseNameWithoutExtension,
                    batchIndexOneBased = 1,
                    batchTotalCount = 1,
                    targetFormat = targetFormat
                )
                inputs.size == 1 -> first.baseNameWithoutExtension
                else -> "${first.baseNameWithoutExtension}_lote_${inputs.size}"
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
        uniqueEntryName: (InputFileItem) -> String,
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
                val entryName = uniqueEntryName(item)
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
        val selectedEntries = options.selectedArchiveEntries

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
                SupportedFormat.ZIP -> extractZipArchive(item, subFolderName, selectedEntries) { subP, msg ->
                    onProgress((baseProgress + subP * slice).coerceIn(0f, 0.99f), msg)
                }
                SupportedFormat.TAR -> extractTarArchive(item, subFolderName, TarCompression.NONE, selectedEntries) { subP, msg ->
                    onProgress((baseProgress + subP * slice).coerceIn(0f, 0.99f), msg)
                }
                SupportedFormat.TAR_GZ -> extractTarArchive(item, subFolderName, TarCompression.GZIP, selectedEntries) { subP, msg ->
                    onProgress((baseProgress + subP * slice).coerceIn(0f, 0.99f), msg)
                }
                SupportedFormat.TAR_XZ -> extractTarArchive(item, subFolderName, TarCompression.XZ, selectedEntries) { subP, msg ->
                    onProgress((baseProgress + subP * slice).coerceIn(0f, 0.99f), msg)
                }
                SupportedFormat.TAR_BZ2 -> extractTarArchive(item, subFolderName, TarCompression.BZIP2, selectedEntries) { subP, msg ->
                    onProgress((baseProgress + subP * slice).coerceIn(0f, 0.99f), msg)
                }
                SupportedFormat.GZ -> extractSingleCompressedStreamArchive(
                    item = item,
                    rootSubFolder = subFolderName,
                    compression = TarCompression.GZIP,
                    suffixesToStrip = listOf(".gz", ".GZ"),
                    selectedEntries = selectedEntries
                ) { subP, msg ->
                    onProgress((baseProgress + subP * slice).coerceIn(0f, 0.99f), msg)
                }
                SupportedFormat.XZ -> extractSingleCompressedStreamArchive(
                    item = item,
                    rootSubFolder = subFolderName,
                    compression = TarCompression.XZ,
                    suffixesToStrip = listOf(".xz", ".XZ"),
                    selectedEntries = selectedEntries
                ) { subP, msg ->
                    onProgress((baseProgress + subP * slice).coerceIn(0f, 0.99f), msg)
                }
                SupportedFormat.BZ2 -> extractSingleCompressedStreamArchive(
                    item = item,
                    rootSubFolder = subFolderName,
                    compression = TarCompression.BZIP2,
                    suffixesToStrip = listOf(".bz2", ".BZ2", ".bzip2"),
                    selectedEntries = selectedEntries
                ) { subP, msg ->
                    onProgress((baseProgress + subP * slice).coerceIn(0f, 0.99f), msg)
                }
                SupportedFormat.SEVEN_Z -> extractSevenZArchive(item, subFolderName, selectedEntries) { subP, msg ->
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

    private fun shouldExtractEntry(entryName: String, selectedEntries: Set<String>): Boolean {
        if (selectedEntries.isEmpty()) return true
        return entryName in selectedEntries
    }

    private suspend fun extractZipArchive(
        item: InputFileItem,
        rootSubFolder: String,
        selectedEntries: Set<String>,
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
                        if (!entry.isDirectory && shouldExtractEntry(entry.name, selectedEntries)) {
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
            throw IOException("El archivo ZIP '${item.displayName}' no contiene archivos extraíbles seleccionados.")
        }
        return results
    }

    private suspend fun extractTarArchive(
        item: InputFileItem,
        rootSubFolder: String,
        compression: TarCompression,
        selectedEntries: Set<String>,
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
                            if (!entry.isDirectory && shouldExtractEntry(entry.name, selectedEntries)) {
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
            throw IOException("El archivo TAR '${item.displayName}' no contiene archivos extraíbles seleccionados.")
        }
        return results
    }

    private suspend fun extractSingleCompressedStreamArchive(
        item: InputFileItem,
        rootSubFolder: String,
        compression: TarCompression,
        suffixesToStrip: List<String>,
        selectedEntries: Set<String>,
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
            return extractTarArchive(item, rootSubFolder, compression, selectedEntries, onSubProgress)
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
        selectedEntries: Set<String>,
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
                    val entryName = entry.name ?: "archivo_${currentIdx + 1}"
                    if (!entry.isDirectory && shouldExtractEntry(entryName, selectedEntries)) {
                        currentIdx++
                        val p = (currentIdx.toFloat() / entriesCount.toFloat()).coerceIn(0f, 0.98f)
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
                    } else if (!entry.isDirectory) {
                        // Drenar entrada omitida para avanzar puntero de SevenZFile
                        while (sevenZFile.read(buffer, 0, buffer.size) > 0) {
                            coroutineContext.ensureActive()
                        }
                    }
                    entry = sevenZFile.nextEntry
                }
            }
        } finally {
            temp7z.delete()
        }

        if (results.isEmpty()) {
            throw IOException("El archivo 7Z '${item.displayName}' no contiene archivos extraíbles seleccionados.")
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
