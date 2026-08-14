package com.andersonlin.moneybook.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.data.model.DefaultCategories

@Database(
    entities = [Bill::class, Category::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun billDao(): BillDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "moneybook.db"
                )
                    .addCallback(seedCallback)
                    .build()
                    .also { instance = it }
            }

        /** 首次创建数据库时写入内置默认分类 */
        private val seedCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                DefaultCategories.ALL.forEach { c ->
                    db.execSQL(
                        "INSERT INTO categories (name, type, icon, isDefault, sortOrder) VALUES (?, ?, ?, ?, ?)",
                        arrayOf(c.name, c.type, c.icon, if (c.isDefault) 1 else 0, c.sortOrder)
                    )
                }
            }
        }
    }
}
