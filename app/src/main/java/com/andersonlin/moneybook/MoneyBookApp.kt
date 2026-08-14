package com.andersonlin.moneybook

import android.app.Application
import com.andersonlin.moneybook.data.backup.BackupManager
import com.andersonlin.moneybook.data.db.AppDatabase
import com.andersonlin.moneybook.data.reminder.ReminderRepository
import com.andersonlin.moneybook.data.reminder.ReminderScheduler
import com.andersonlin.moneybook.data.repository.AccountRepository
import com.andersonlin.moneybook.data.repository.BillRepository
import com.andersonlin.moneybook.data.repository.BudgetRepository
import com.andersonlin.moneybook.data.repository.CategoryRepository
import com.andersonlin.moneybook.data.repository.GoalRepository
import com.andersonlin.moneybook.data.repository.LedgerRepository
import com.andersonlin.moneybook.data.repository.RecurringRepository
import com.andersonlin.moneybook.data.saving.SavingDepositService
import com.andersonlin.moneybook.data.settings.LockSettingsRepository
import com.andersonlin.moneybook.data.settings.SettingsRepository
import com.andersonlin.moneybook.widget.MoneyBookWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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
    val ledgerRepository: LedgerRepository by lazy {
        LedgerRepository(this, database, database.ledgerDao())
    }
    val goalRepository: GoalRepository by lazy { GoalRepository(database.goalDao()) }
    val savingDepositService: SavingDepositService by lazy { SavingDepositService(database) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val lockSettingsRepository: LockSettingsRepository by lazy { LockSettingsRepository(this) }
    val reminderRepository: ReminderRepository by lazy { ReminderRepository(this) }
    val backupManager: BackupManager by lazy { BackupManager(this, database) }

    override fun onCreate() {
        super.onCreate()
        // 启动时补记到期的周期账单（失败不影响使用）
        applicationScope.launch {
            runCatching { recurringRepository.generateDue() }
        }
        // 启动时同步记账提醒调度（已开启则按设定时间重新调度）
        applicationScope.launch {
            runCatching {
                val s = reminderRepository.settings.first()
                if (s.enabled) {
                    ReminderScheduler.schedule(this@MoneyBookApp, s.hour, s.minute)
                }
            }
        }
    }

    /** 请求刷新桌面小组件（记账保存后、回到前台时调用） */
    fun requestWidgetUpdate() {
        applicationScope.launch {
            runCatching { MoneyBookWidget.updateAllWidgets(this@MoneyBookApp) }
        }
    }
}
