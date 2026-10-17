package com.mukunjin.virgube.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Virgube 本地数据库（成绩与会话）。 */
@Database(
    entities = [SessionEntity::class, SolveEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class VirgubeDatabase : RoomDatabase() {

    abstract fun dao(): VirgubeDao

    companion object {
        private const val DB_NAME = "virgube.db"

        @Volatile
        private var instance: VirgubeDatabase? = null

        fun get(context: Context): VirgubeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VirgubeDatabase::class.java,
                    DB_NAME,
                ).build().also { instance = it }
            }
    }
}