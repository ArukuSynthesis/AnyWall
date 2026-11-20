package com.anywall.converter.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.anywall.converter.data.local.dao.ConversionHistoryDao
import com.anywall.converter.data.local.entity.ConversionHistoryEntity

/**
 * Base de datos local Room 100% offline de AnyWall: Universal Converter (Esquema v2).
 */
@Database(
    entities = [ConversionHistoryEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AnyWallDatabase : RoomDatabase() {

    abstract fun conversionHistoryDao(): ConversionHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AnyWallDatabase? = null

        /**
         * Migración oficial de esquema v1 (AnyWall v0.1) a v2 (AnyWall v0.2):
         * añade `source_size_bytes` para calcular el porcentaje de ahorro de espacio.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE conversion_history ADD COLUMN source_size_bytes INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getInstance(context: Context): AnyWallDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnyWallDatabase::class.java,
                    "anywall_converter.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
