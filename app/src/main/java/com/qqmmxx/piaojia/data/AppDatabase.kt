package com.qqmmxx.piaojia.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.qqmmxx.piaojia.data.dao.ExpenseDao
import com.qqmmxx.piaojia.data.dao.ProjectDao
import com.qqmmxx.piaojia.model.ExpenseEntity
import com.qqmmxx.piaojia.model.ProjectEntity

@Database(
    entities = [ProjectEntity::class, ExpenseEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        private const val DB_NAME = "expense_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    // 目前 schema 尚未稳定，版本升级时直接重建库。
                    // 注意：这意味着升级会丢失本地数据，用户数据可通过导出的 Excel 重新导入。
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
