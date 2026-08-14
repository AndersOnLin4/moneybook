package com.andersonlin.moneybook.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andersonlin.moneybook.MoneyBookApp
import com.andersonlin.moneybook.ui.account.AccountViewModel
import com.andersonlin.moneybook.ui.bill.AddEditBillViewModel
import com.andersonlin.moneybook.ui.bill.BillListViewModel
import com.andersonlin.moneybook.ui.budget.BudgetViewModel
import com.andersonlin.moneybook.ui.category.CategoryViewModel
import com.andersonlin.moneybook.ui.categorybudget.CategoryBudgetViewModel
import com.andersonlin.moneybook.ui.goal.GoalViewModel
import com.andersonlin.moneybook.ui.home.HomeViewModel
import com.andersonlin.moneybook.ui.ledger.LedgerViewModel
import com.andersonlin.moneybook.ui.lock.LockViewModel
import com.andersonlin.moneybook.ui.onboarding.OnboardingViewModel
import com.andersonlin.moneybook.ui.recurring.RecurringViewModel
import com.andersonlin.moneybook.ui.reminder.ReminderViewModel
import com.andersonlin.moneybook.ui.settings.SettingsViewModel
import com.andersonlin.moneybook.ui.stats.StatsViewModel

/** 统一的 ViewModel 工厂：从 Application 容器取出仓库注入 */
object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer {
            HomeViewModel(
                app().billRepository,
                app().categoryRepository,
                app().accountRepository,
                app().budgetRepository,
                app().ledgerRepository,
                app().goalRepository,
                app().categoryBudgetRepository
            )
        }
        initializer {
            BillListViewModel(
                app().billRepository,
                app().categoryRepository,
                app().accountRepository,
                app().ledgerRepository
            )
        }
        initializer {
            AddEditBillViewModel(
                app().billRepository,
                app().categoryRepository,
                app().accountRepository,
                app().ledgerRepository
            )
        }
        initializer {
            StatsViewModel(app().billRepository, app().categoryRepository, app().ledgerRepository)
        }
        initializer {
            SettingsViewModel(
                app().settingsRepository,
                app().backupManager,
                app().ledgerRepository,
                app().lockSettingsRepository
            )
        }
        initializer {
            CategoryViewModel(app().categoryRepository, app().billRepository, app().recurringRepository)
        }
        initializer { AccountViewModel(app().accountRepository) }
        initializer { BudgetViewModel(app().budgetRepository) }
        initializer {
            CategoryBudgetViewModel(
                app().categoryRepository,
                app().categoryBudgetRepository,
                app().billRepository,
                app().ledgerRepository
            )
        }
        initializer {
            RecurringViewModel(
                app().recurringRepository,
                app().categoryRepository,
                app().accountRepository,
                app().ledgerRepository
            )
        }
        initializer { LockViewModel(app().lockSettingsRepository) }
        initializer { LedgerViewModel(app().ledgerRepository, app().billRepository) }
        initializer {
            GoalViewModel(app().goalRepository, app().savingDepositService, app().ledgerRepository)
        }
        initializer { ReminderViewModel(app().reminderRepository) }
        initializer {
            OnboardingViewModel(
                app().ledgerRepository,
                app().budgetRepository,
                app().recurringRepository,
                app().goalRepository,
                app().categoryRepository,
                app().lockSettingsRepository,
                app().settingsRepository
            )
        }
    }
}

fun CreationExtras.app(): MoneyBookApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyBookApp
