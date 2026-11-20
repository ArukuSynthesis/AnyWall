package com.anywall.converter.data.local

import com.anywall.converter.core.ScopedStorageManager
import com.anywall.converter.data.local.dao.ConversionHistoryDao
import com.anywall.converter.data.local.entity.ConversionHistoryEntity
import com.anywall.converter.domain.model.ConversionJobResult
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.HistoryRecord
import com.anywall.converter.domain.repository.ConversionHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Implementación del repositorio de historial de conversiones respaldada por Room y ScopedStorageManager.
 */
class ConversionHistoryRepositoryImpl(
    private val dao: ConversionHistoryDao,
    private val storageManager: ScopedStorageManager
) : ConversionHistoryRepository {

    override fun observeAll(): Flow<List<HistoryRecord>> {
        return dao.observeAll().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun observeByCategory(category: FormatCategory): Flow<List<HistoryRecord>> {
        return dao.observeByCategory(category.name).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun recordConversion(result: ConversionJobResult): Long =
        withContext(Dispatchers.IO) {
            dao.insert(ConversionHistoryEntity.fromJobResult(result))
        }

    override suspend fun recordConversions(results: List<ConversionJobResult>) =
        withContext(Dispatchers.IO) {
            if (results.isNotEmpty()) {
                dao.insertAll(results.map(ConversionHistoryEntity::fromJobResult))
            }
        }

    override suspend fun deleteRecord(
        record: HistoryRecord,
        deletePhysicalFile: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (deletePhysicalFile) {
            storageManager.deleteSavedOutput(record.outputUriString, record.outputAbsolutePath)
        }
        dao.deleteById(record.id) > 0
    }

    override suspend fun deleteRecords(
        records: List<HistoryRecord>,
        deletePhysicalFiles: Boolean
    ): Int = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext 0
        if (deletePhysicalFiles) {
            for (record in records) {
                storageManager.deleteSavedOutput(record.outputUriString, record.outputAbsolutePath)
            }
        }
        dao.deleteByIds(records.map { it.id })
    }

    override suspend fun clearAll(deletePhysicalFiles: Boolean) = withContext(Dispatchers.IO) {
        if (deletePhysicalFiles) {
            val all = dao.getAllSnapshot()
            for (entity in all) {
                storageManager.deleteSavedOutput(entity.outputUriString, entity.outputAbsolutePath)
            }
        }
        dao.deleteAll()
    }
}
