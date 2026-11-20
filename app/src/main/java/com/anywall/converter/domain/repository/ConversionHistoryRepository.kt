package com.anywall.converter.domain.repository

import com.anywall.converter.domain.model.ConversionJobResult
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.HistoryRecord
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para gestionar el historial local de conversiones en Room (v0.2).
 */
interface ConversionHistoryRepository {
    fun observeAll(): Flow<List<HistoryRecord>>

    fun observeByCategory(category: FormatCategory): Flow<List<HistoryRecord>>

    suspend fun recordConversion(result: ConversionJobResult): Long

    suspend fun recordConversions(results: List<ConversionJobResult>)

    suspend fun deleteRecord(record: HistoryRecord, deletePhysicalFile: Boolean): Boolean

    suspend fun deleteRecords(records: List<HistoryRecord>, deletePhysicalFiles: Boolean): Int

    suspend fun clearAll(deletePhysicalFiles: Boolean)
}
