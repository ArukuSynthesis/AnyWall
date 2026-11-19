package com.anywall.converter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anywall.converter.data.local.entity.ConversionHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO de Room para consultar, insertar y eliminar registros del historial de conversiones.
 */
@Dao
interface ConversionHistoryDao {

    @Query("SELECT * FROM conversion_history ORDER BY created_at_millis DESC")
    fun observeAll(): Flow<List<ConversionHistoryEntity>>

    @Query("SELECT * FROM conversion_history WHERE category = :categoryName ORDER BY created_at_millis DESC")
    fun observeByCategory(categoryName: String): Flow<List<ConversionHistoryEntity>>

    @Query("SELECT * FROM conversion_history ORDER BY created_at_millis DESC")
    suspend fun getAllSnapshot(): List<ConversionHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ConversionHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ConversionHistoryEntity>)

    @Query("DELETE FROM conversion_history WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM conversion_history")
    suspend fun deleteAll()
}
