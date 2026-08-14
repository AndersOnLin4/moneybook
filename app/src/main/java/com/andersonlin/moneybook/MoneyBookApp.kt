package com.andersonlin.moneybook

import android.app.Application
import com.andersonlin.moneybook.data.backup.BackupManager
import com.andersonlin.moneybook.data.db.AppDatabase
import com.andersonlin.moneybook.data.repository.BillRepository
import com.andersonlin.moneybook.data.repository.CategoryRepository
import com.andersonlin.moneybook.data.settings.SettingsRepository

/** 应用入口：集中创建数据库与仓库，供 ViewModel 工厂取用 */
class MoneyBookApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val billRepository: BillRepository by lazy { BillRepository(database.billDao()) }
    val categoryRepository: CategoryRepository by lazy { CategoryRepository(database.categoryDao()) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val backupManager: BackupManager by lazy { BackupManager(this, database) }
}
