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
import com.andersonlin.moneybook.data.model.DefaultLedgers
import com.andersonlin.moneybook.data.model.Goal
import com.andersonlin.moneybook.data.model.Ledger
import com.andersonlin.moneybook.data.model.RecurringBill

@Database(
    entities = [
        Bill::class, Category::class, Account::class, Budget::class,
        RecurringBill::class, Ledger::class, Goal::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun billDao(): BillDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringBillDao(): RecurringBillDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun goalDao(): GoalDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }

        /** 首次创建数据库时写入内置默认分类、默认账户与默认账本 */
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
                seedLedgers(db)
            }
        }

        private fun seedLedgers(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT INTO ledgers (name, icon, sortOrder, createdAt) VALUES (?, ?, ?, ?)",
                arrayOf(DefaultLedgers.DEFAULT.name, DefaultLedgers.DEFAULT.icon, 1, System.currentTimeMillis())
            )
        }

        /**
         * v1 → v2：新增账户、月度预算、周期账单（详见 v1.1.x 历史）。
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
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_categoryId ON bills (categoryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_dateEpochDay ON bills (dateEpochDay)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_type ON bills (type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_accountId ON bills (accountId)")
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

        /**
         * v2 → v3：多账本 + 存钱目标。
         * 1. 新建 ledgers 表并写入「默认账本」（id = 1）
         * 2. 重建 bills：新增 ledgerId 列 + 账本外键（旧账单归入默认账本）
         * 3. 重建 recurring_bills：新增 ledgerId 列 + 账本外键
         * 4. 新建 goals 表
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS ledgers (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, icon TEXT NOT NULL, " +
                        "sortOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
                seedLedgers(db)

                // bills 重建：+ ledgerId
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bills_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "type INTEGER NOT NULL, amountCents INTEGER NOT NULL, " +
                        "categoryId INTEGER NOT NULL, accountId INTEGER NOT NULL, " +
                        "ledgerId INTEGER NOT NULL, " +
                        "note TEXT NOT NULL, dateEpochDay INTEGER NOT NULL, createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(categoryId) REFERENCES categories(id) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                        "FOREIGN KEY(accountId) REFERENCES accounts(id) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                        "FOREIGN KEY(ledgerId) REFERENCES ledgers(id) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT)"
                )
                db.execSQL(
                    "INSERT INTO bills_new " +
                        "(id, type, amountCents, categoryId, accountId, ledgerId, note, dateEpochDay, createdAt) " +
                        "SELECT id, type, amountCents, categoryId, accountId, 1, note, dateEpochDay, createdAt FROM bills"
                )
                db.execSQL("DROP TABLE bills")
                db.execSQL("ALTER TABLE bills_new RENAME TO bills")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_categoryId ON bills (categoryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_dateEpochDay ON bills (dateEpochDay)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_type ON bills (type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_accountId ON bills (accountId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_ledgerId ON bills (ledgerId)")

                // recurring_bills 重建：+ ledgerId
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS recurring_bills_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "type INTEGER NOT NULL, amountCents INTEGER NOT NULL, " +
                        "categoryId INTEGER NOT NULL, accountId INTEGER NOT NULL, " +
                        "ledgerId INTEGER NOT NULL, " +
                        "note TEXT NOT NULL, cycle INTEGER NOT NULL, " +
                        "startEpochDay INTEGER NOT NULL, lastGeneratedEpochDay INTEGER NOT NULL, " +
                        "enabled INTEGER NOT NULL, " +
                        "FOREIGN KEY(categoryId) REFERENCES categories(id) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                        "FOREIGN KEY(accountId) REFERENCES accounts(id) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                        "FOREIGN KEY(ledgerId) REFERENCES ledgers(id) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT)"
                )
                db.execSQL(
                    "INSERT INTO recurring_bills_new " +
                        "(id, type, amountCents, categoryId, accountId, ledgerId, note, cycle, " +
                        "startEpochDay, lastGeneratedEpochDay, enabled) " +
                        "SELECT id, type, amountCents, categoryId, accountId, 1, note, cycle, " +
                        "startEpochDay, lastGeneratedEpochDay, enabled FROM recurring_bills"
                )
                db.execSQL("DROP TABLE recurring_bills")
                db.execSQL("ALTER TABLE recurring_bills_new RENAME TO recurring_bills")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recurring_bills_categoryId ON recurring_bills (categoryId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recurring_bills_accountId ON recurring_bills (accountId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recurring_bills_ledgerId ON recurring_bills (ledgerId)"
                )

                // goals 表
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS goals (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, icon TEXT NOT NULL, " +
                        "targetCents INTEGER NOT NULL, savedCents INTEGER NOT NULL, " +
                        "deadlineEpochDay INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
            }
        }
    }
}
