package com.andersonlin.moneybook

import android.app.Application
import com.andersonlin.moneybook.data.backup.BackupManager
import com.andersonlin.moneybook.data.db.AppDatabase
import com.andersonlin.moneybook.data.repository.AccountRepository
import com.andersonlin.moneybook.data.repository.BillRepository
import com.andersonlin.moneybook.data.repository.BudgetRepository
import com.andersonlin.moneybook.data.repository.CategoryRepository
import com.andersonlin.moneybook.data.repository.RecurringRepository
import com.andersonlin.moneybook.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 应用入口：集中创建数据库与仓库，供 ViewModel 工厂取用 */
class MoneyBookApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val billRepository: BillRepository by lazy { BillRepository(database.billDao()) }
    val categoryRepository: CategoryRepository by lazy { CategoryRepository(database.categoryDao()) }
    val accountRepository: AccountRepository by lazy { AccountRepository(database, database.accountDao()) }
    val budgetRepository: BudgetRepository by lazy { BudgetRepository(database.budgetDao()) }
    val recurringRepository: RecurringRepository by lazy {
        RecurringRepository(database, database.recurringBillDao())
    }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val backupManager: BackupManager by lazy { BackupManager(this, database) }

    override fun onCreate() {
        super.onCreate()
        // 启动时补记到期的周期账单（失败不影响使用）
        applicationScope.launch {
            runCatching { recurringRepository.generateDue() }
        }
    }
}
