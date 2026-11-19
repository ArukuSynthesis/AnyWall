package com.anywall.converter.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.anywall.converter.data.local.dao.ConversionHistoryDao
import com.anywall.converter.data.local.entity.ConversionHistoryEntity

/**
 * Base de datos local Room 100% offline de AnyWall: Universal Converter.
 */
@Database(
    entities = [ConversionHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AnyWallDatabase : RoomDatabase() {

    abstract fun conversionHistoryDao(): ConversionHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AnyWallDatabase? = null

        fun getInstance(context: Context): AnyWallDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnyWallDatabase::class.java,
                    "anywall_converter.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
