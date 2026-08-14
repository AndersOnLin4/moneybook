package com.andersonlin.moneybook.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andersonlin.moneybook.MoneyBookApp
import com.andersonlin.moneybook.ui.account.AccountViewModel
import com.andersonlin.moneybook.ui.bill.AddEditBillViewModel
import com.andersonlin.moneybook.ui.bill.BillListViewModel
import com.andersonlin.moneybook.ui.category.CategoryViewModel
import com.andersonlin.moneybook.ui.home.HomeViewModel
import com.andersonlin.moneybook.ui.settings.SettingsViewModel
import com.andersonlin.moneybook.ui.stats.StatsViewModel

/** 统一的 ViewModel 工厂：从 Application 容器取出仓库注入 */
object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer {
            HomeViewModel(app().billRepository, app().categoryRepository, app().accountRepository)
        }
        initializer {
            BillListViewModel(app().billRepository, app().categoryRepository, app().accountRepository)
        }
        initializer {
            AddEditBillViewModel(app().billRepository, app().categoryRepository, app().accountRepository)
        }
        initializer { StatsViewModel(app().billRepository, app().categoryRepository) }
        initializer { SettingsViewModel(app().settingsRepository, app().backupManager) }
        initializer {
            CategoryViewModel(app().categoryRepository, app().billRepository, app().recurringRepository)
        }
        initializer { AccountViewModel(app().accountRepository) }
    }
}

fun CreationExtras.app(): MoneyBookApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyBookApp
