package com.anywall.converter.data.engines

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

/**
 * Divisor y Reconstructor determinista de archivos grandes (`.part001`, `.part002`...)
 * con generación y verificación automática de manifiesto `SHA-256` (v0.3).
 */
object FileSplitterAndJoiner {

    data class SplitResult(
        val partFiles: List<File>,
        val manifestFile: File,
        val originalSha256: String,
        val totalBytes: Long
    )

    data class JoinResult(
        val reconstructedFile: File,
        val originalFileName: String,
        val sha256Hex: String,
        val verifiedWithManifest: Boolean
    )

    @Throws(IOException::class)
    fun splitFileIntoParts(
        sourceFile: File,
        originalFileName: String,
        outputDir: File,
        maxPartSizeBytes: Long
    ): SplitResult {
        require(maxPartSizeBytes >= 1024L) { "El tamaño de cada parte debe ser de al menos 1 KB." }
        if (!outputDir.exists()) outputDir.mkdirs()

        val sha256 = AesVaultCrypto.computeSha256Hex(sourceFile)
        val totalBytes = sourceFile.length()
        val parts = mutableListOf<File>()

        FileInputStream(sourceFile).use { input ->
            val buffer = ByteArray(64 * 1024)
            var partIndex = 1
            var remainingInCurrentPart = 0L
            var currentOut: FileOutputStream? = null

            try {
                while (true) {
                    if (currentOut == null || remainingInCurrentPart <= 0L) {
                        currentOut?.flush()
                        currentOut?.close()
                        currentOut = null

                        val partSuffix = String.format(Locale.US, ".part%03d", partIndex)
                        val partFile = File(outputDir, "$originalFileName$partSuffix")
                        val nextByte = input.read()
                        if (nextByte == -1) break

                        parts.add(partFile)
                        currentOut = FileOutputStream(partFile)
                        currentOut.write(nextByte)
                        remainingInCurrentPart = maxPartSizeBytes - 1L
                        partIndex++
                    }

                    if (remainingInCurrentPart > 0L) {
                        val toRead = minOf(buffer.size.toLong(), remainingInCurrentPart).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read == -1) break
                        currentOut.write(buffer, 0, read)
                        remainingInCurrentPart -= read.toLong()
                    }
                }
            } finally {
                currentOut?.flush()
                currentOut?.close()
            }
        }

        if (parts.isEmpty()) {
            // Archivo vacío: generar .part001 vacío
            val emptyPart = File(outputDir, "$originalFileName.part001")
            emptyPart.writeBytes(ByteArray(0))
            parts.add(emptyPart)
        }

        val manifestFile = File(outputDir, "$originalFileName.anywall-parts.txt")
        val manifestContent = buildString {
            appendLine("ANYWALL_PARTS_MANIFEST=v0.3")
            appendLine("ORIGINAL_FILE_NAME=$originalFileName")
            appendLine("ORIGINAL_SIZE_BYTES=$totalBytes")
            appendLine("ORIGINAL_SHA256=$sha256")
            appendLine("TOTAL_PARTS=${parts.size}")
            parts.forEachIndexed { idx, part ->
                appendLine("PART_${idx + 1}=${part.name} (${part.length()} bytes)")
            }
        }
        manifestFile.writeText(manifestContent, Charsets.UTF_8)

        return SplitResult(
            partFiles = parts,
            manifestFile = manifestFile,
            originalSha256 = sha256,
            totalBytes = totalBytes
        )
    }

    @Throws(IOException::class)
    fun joinPartFiles(
        partFiles: List<File>,
        outputFile: File,
        expectedSha256Hex: String? = null
    ): JoinResult {
        require(partFiles.isNotEmpty()) { "No se proporcionaron partes para reconstruir el archivo." }

        val sortedParts = partFiles.sortedBy { extractPartNumber(it.name) }
        val inferredOriginalName = inferOriginalFileName(sortedParts.first().name)

        FileOutputStream(outputFile).use { out ->
            for (part in sortedParts) {
                FileInputStream(part).use { input ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                }
            }
            out.flush()
        }

        val actualSha256 = AesVaultCrypto.computeSha256Hex(outputFile)
        val cleanExpected = expectedSha256Hex?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (cleanExpected.isNotEmpty() && !cleanExpected.equals(actualSha256, ignoreCase = true)) {
            throw IOException(
                "Error de integridad SHA-256 al unir partes: esperado $cleanExpected pero se obtuvo $actualSha256."
            )
        }

        return JoinResult(
            reconstructedFile = outputFile,
            originalFileName = inferredOriginalName,
            sha256Hex = actualSha256,
            verifiedWithManifest = cleanExpected.isNotEmpty()
        )
    }

    fun extractPartNumber(fileName: String): Int {
        val match = Regex("\\.part(\\d+)$|\\.(\\d{3})$", RegexOption.IGNORE_CASE).find(fileName.trim())
        val digits = match?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
        return digits?.toIntOrNull() ?: Int.MAX_VALUE
    }

    fun inferOriginalFileName(partFileName: String): String {
        return partFileName
            .replace(Regex("\\.part\\d+$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\.\\d{3}$"), "")
            .ifBlank { "archivo_reconstruido.bin" }
    }

    fun parseExpectedSha256FromManifest(manifestText: String): String? {
        for (line in manifestText.lines()) {
            if (line.startsWith("ORIGINAL_SHA256=", ignoreCase = true)) {
                return line.substringAfter('=').trim().takeIf { it.isNotEmpty() }
            }
        }
        return null
    }
}
