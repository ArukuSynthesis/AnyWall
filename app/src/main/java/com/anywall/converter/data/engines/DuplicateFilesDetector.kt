package com.anywall.converter.data.engines

import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.domain.model.InputFileItem
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Detector y limpiador de archivos duplicados en 2 fases (`DuplicateFilesDetector` - v0.4):
 * 1. Agrupación rápida $O(N)$ por tamaño exacto en bytes (`sizeBytes`): descarta al instante archivos de tamaño único.
 * 2. Cálculo de hash criptográfico `SHA-256` en streaming (bloques de 64 KB) solo para los candidatos del mismo tamaño.
 */
object DuplicateFilesDetector {

    data class DuplicateGroup<T>(
        val sha256Hex: String,
        val fileSizeBytes: Long,
        val items: List<T>
    ) {
        val keeper: T get() = items.first()
        val redundantDuplicates: List<T> get() = items.drop(1)
        val reclaimableBytes: Long get() = fileSizeBytes * (items.size - 1).coerceAtLeast(0)
    }

    data class DuplicateScanReport<T>(
        val totalScannedCount: Int,
        val duplicateGroups: List<DuplicateGroup<T>>,
        val deduplicatedList: List<T>
    ) {
        val totalRedundantFilesCount: Int
            get() = duplicateGroups.sumOf { it.redundantDuplicates.size }

        val totalReclaimableBytes: Long
            get() = duplicateGroups.sumOf { it.reclaimableBytes }

        val hasDuplicates: Boolean
            get() = duplicateGroups.isNotEmpty()
    }

    /**
     * Calcula el hash `SHA-256` hexadecimal en minúsculas leyendo un `InputStream` en streaming (bloques de 64 KB).
     */
    fun computeStreamingSha256Hex(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = inputStream.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun computeFileStreamingSha256Hex(file: File): String {
        FileInputStream(file).use { fis ->
            return computeStreamingSha256Hex(fis)
        }
    }

    /**
     * Escanea una lista de `File` en disco para detectar duplicados exactos por tamaño + `SHA-256` en streaming.
     */
    fun scanFilesForDuplicates(files: List<File>): DuplicateScanReport<File> {
        return scanGenericItems(
            items = files,
            sizeExtractor = { it.length() },
            streamOpener = { FileInputStream(it) }
        )
    }

    /**
     * Escanea una lista de `InputFileItem` de la cola de AnyWall para detectar y limpiar duplicados exactos.
     */
    fun scanInputItemsForDuplicates(
        items: List<InputFileItem>,
        uriResolver: UriFileResolver
    ): DuplicateScanReport<InputFileItem> {
        return scanGenericItems(
            items = items,
            sizeExtractor = { it.sizeBytes },
            streamOpener = { uriResolver.openInputStream(it.uri) }
        )
    }

    fun <T> scanGenericItems(
        items: List<T>,
        sizeExtractor: (T) -> Long,
        streamOpener: (T) -> InputStream
    ): DuplicateScanReport<T> {
        if (items.size <= 1) {
            return DuplicateScanReport(
                totalScannedCount = items.size,
                duplicateGroups = emptyList(),
                deduplicatedList = items
            )
        }

        // Fase 1: Agrupar por tamaño exacto en bytes
        val bySize = items.groupBy { sizeExtractor(it).coerceAtLeast(0L) }
        val hashByItemIndex = HashMap<Int, String>()

        items.forEachIndexed { index, item ->
            val sz = sizeExtractor(item).coerceAtLeast(0L)
            val sameSizeBucket = bySize[sz].orEmpty()
            // Solo calcular SHA-256 en streaming si hay >= 2 archivos con exactamente el mismo tamaño (o si sizeBytes == 0 desconocido)
            if (sameSizeBucket.size >= 2 || sz == 0L) {
                val hash = try {
                    streamOpener(item).use { stream ->
                        computeStreamingSha256Hex(stream)
                    }
                } catch (_: Throwable) {
                    "err_$index"
                }
                hashByItemIndex[index] = hash
            }
        }

        // Fase 2: Agrupar candidatos por SHA-256 y construir lista deduplicada preservando el orden original
        val seenHashes = mutableSetOf<String>()
        val deduplicated = ArrayList<T>(items.size)
        val groupedByHash = linkedMapOf<String, MutableList<T>>()
        val sizeByHash = HashMap<String, Long>()

        items.forEachIndexed { index, item ->
            val hash = hashByItemIndex[index]
            if (hash == null || hash.startsWith("err_")) {
                deduplicated.add(item)
            } else {
                groupedByHash.getOrPut(hash) { mutableListOf() }.add(item)
                sizeByHash[hash] = sizeExtractor(item).coerceAtLeast(0L)
                if (seenHashes.add(hash)) {
                    deduplicated.add(item)
                }
            }
        }

        val duplicateGroups = groupedByHash.entries
            .filter { it.value.size >= 2 }
            .map { (sha, groupItems) ->
                DuplicateGroup(
                    sha256Hex = sha,
                    fileSizeBytes = sizeByHash[sha] ?: 0L,
                    items = groupItems
                )
            }

        return DuplicateScanReport(
            totalScannedCount = items.size,
            duplicateGroups = duplicateGroups,
            deduplicatedList = deduplicated
        )
    }
}
