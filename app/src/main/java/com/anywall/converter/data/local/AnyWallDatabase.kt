package com.anywall.converter.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.anywall.converter.data.local.dao.ConversionHistoryDao
import com.anywall.converter.data.local.dao.ConversionPresetDao
import com.anywall.converter.data.local.entity.ConversionHistoryEntity
import com.anywall.converter.data.local.entity.ConversionPresetEntity

/**
 * Base de datos local Room 100% offline de AnyWall: Universal Converter (Esquema v3 - v0.3).
 */
@Database(
    entities = [
        ConversionHistoryEntity::class,
        ConversionPresetEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AnyWallDatabase : RoomDatabase() {

    abstract fun conversionHistoryDao(): ConversionHistoryDao

    abstract fun conversionPresetDao(): ConversionPresetDao

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

        /**
         * Migración oficial de esquema v2 (AnyWall v0.2) a v3 (AnyWall v0.3):
         * añade la tabla `conversion_presets` para perfiles de conversión rápida.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `conversion_presets` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `target_format` TEXT NOT NULL,
                        `options_serialized` TEXT NOT NULL,
                        `is_built_in` INTEGER NOT NULL,
                        `created_at_millis` INTEGER NOT NULL
                    )
                    """.trimIndent()
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
