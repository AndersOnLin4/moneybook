package com.andersonlin.moneybook.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.andersonlin.moneybook.data.model.Account
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.Budget
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.data.model.DefaultAccounts
import com.andersonlin.moneybook.data.model.DefaultCategories
import com.andersonlin.moneybook.data.model.RecurringBill

@Database(
    entities = [Bill::class, Category::class, Account::class, Budget::class, RecurringBill::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun billDao(): BillDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringBillDao(): RecurringBillDao

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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }

        /** 首次创建数据库时写入内置默认分类与默认账户 */
        private val seedCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                DefaultCategories.ALL.forEach { c ->
                    db.execSQL(
                        "INSERT INTO categories (name, type, icon, isDefault, sortOrder) VALUES (?, ?, ?, ?, ?)",
                        arrayOf(c.name, c.type, c.icon, if (c.isDefault) 1 else 0, c.sortOrder)
                    )
                }
                DefaultAccounts.ALL.forEach { a ->
                    db.execSQL(
                        "INSERT INTO accounts (name, icon, isDefault, sortOrder) VALUES (?, ?, ?, ?)",
                        arrayOf(a.name, a.icon, if (a.isDefault) 1 else 0, a.sortOrder)
                    )
                }
            }
        }

        /**
         * v1 → v2：
         * 1. 新增 accounts 表并写入默认账户（现金/微信/支付宝/银行卡）
         * 2. 重建 bills 表：新增 accountId 列 + 账户外键（SQLite 的 ALTER 无法加外键，
         *    且需保证列无默认值，与 Room 实体一致），旧数据全部归入「现金」账户（id = 1）
         * 3. 新增 budgets（月度预算）与 recurring_bills（周期账单）表
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS accounts (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, icon TEXT NOT NULL, " +
                        "isDefault INTEGER NOT NULL, sortOrder INTEGER NOT NULL)"
                )
                DefaultAccounts.ALL.forEach { a ->
                    db.execSQL(
                        "INSERT INTO accounts (name, icon, isDefault, sortOrder) VALUES (?, ?, ?, ?)",
                        arrayOf(a.name, a.icon, if (a.isDefault) 1 else 0, a.sortOrder)
                    )
                }
                // 重建 bills：加 accountId 列（无默认值）+ accounts 外键
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bills_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "type INTEGER NOT NULL, amountCents INTEGER NOT NULL, " +
                        "categoryId INTEGER NOT NULL, accountId INTEGER NOT NULL, " +
                        "note TEXT NOT NULL, dateEpochDay INTEGER NOT NULL, createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(categoryId) REFERENCES categories(id) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                        "FOREIGN KEY(accountId) REFERENCES accounts(id) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT)"
                )
                db.execSQL(
                    "INSERT INTO bills_new " +
                        "(id, type, amountCents, categoryId, accountId, note, dateEpochDay, createdAt) " +
                        "SELECT id, type, amountCents, categoryId, 1, note, dateEpochDay, createdAt FROM bills"
                )
                db.execSQL("DROP TABLE bills")
                db.execSQL("ALTER TABLE bills_new RENAME TO bills")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_bills_categoryId ON bills (categoryId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_bills_dateEpochDay ON bills (dateEpochDay)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_bills_type ON bills (type)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_bills_accountId ON bills (accountId)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS budgets (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "year INTEGER NOT NULL, month INTEGER NOT NULL, " +
                        "amountCents INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_budgets_year_month ON budgets (year, month)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS recurring_bills (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "type INTEGER NOT NULL, amountCents INTEGER NOT NULL, " +
                        "categoryId INTEGER NOT NULL, accountId INTEGER NOT NULL, " +
                        "note TEXT NOT NULL, cycle INTEGER NOT NULL, " +
                        "startEpochDay INTEGER NOT NULL, lastGeneratedEpochDay INTEGER NOT NULL, " +
                        "enabled INTEGER NOT NULL, " +
                        "FOREIGN KEY(categoryId) REFERENCES categories(id) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                        "FOREIGN KEY(accountId) REFERENCES accounts(id) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recurring_bills_categoryId ON recurring_bills (categoryId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recurring_bills_accountId ON recurring_bills (accountId)"
                )
            }
        }
    }
}
