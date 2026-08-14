package com.andersonlin.moneybook.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.data.model.Goal
import com.andersonlin.moneybook.data.model.Ledger
import com.andersonlin.moneybook.data.model.RecurringBill
import com.andersonlin.moneybook.data.repository.BudgetRepository
import com.andersonlin.moneybook.data.repository.CategoryRepository
import com.andersonlin.moneybook.data.repository.GoalRepository
import com.andersonlin.moneybook.data.repository.LedgerRepository
import com.andersonlin.moneybook.data.repository.RecurringRepository
import com.andersonlin.moneybook.data.settings.LockSettings
import com.andersonlin.moneybook.data.settings.LockSettingsRepository
import com.andersonlin.moneybook.data.settings.SettingsRepository
import com.andersonlin.moneybook.data.settings.ThemeMode
import com.andersonlin.moneybook.util.toCents
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

sealed interface OnboardingEvent {
    data class ShowMessage(val message: String) : OnboardingEvent
}

/** 首次引导：引导用户完成账本/预算/周期账单/存钱目标/安全与外观配置（每步可跳过） */
class OnboardingViewModel(
    private val ledgerRepository: LedgerRepository,
    private val budgetRepository: BudgetRepository,
    private val recurringRepository: RecurringRepository,
    private val goalRepository: GoalRepository,
    private val categoryRepository: CategoryRepository,
    private val lockSettingsRepository: LockSettingsRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val ledgers: StateFlow<List<Ledger>> = ledgerRepository.getAllLedgers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expenseCategories: StateFlow<List<Category>> = categoryRepository
        .getCategoriesByType(Bill.TYPE_EXPENSE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lockSettings: StateFlow<LockSettings> = lockSettingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockSettings())

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    private val _events = MutableSharedFlow<OnboardingEvent>()
    val events = _events.asSharedFlow()

    fun addLedger(name: String, icon: String, onDone: () -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            emit("账本名称不能为空")
            return
        }
        viewModelScope.launch {
            if (ledgerRepository.getAllSnapshot().any { it.name == trimmed }) {
                _events.emit(OnboardingEvent.ShowMessage("该账本已存在"))
                return@launch
            }
            ledgerRepository.addLedger(trimmed, icon)
            _events.emit(OnboardingEvent.ShowMessage("已创建账本「$trimmed」"))
            onDone()
        }
    }

    fun setMonthBudget(amountText: String, onDone: () -> Unit) {
        val cents = amountText.toCents() ?: run {
            emit("请输入有效金额")
            return
        }
        val now = YearMonth.now()
        viewModelScope.launch {
            budgetRepository.setBudget(now.year, now.monthValue, cents)
            _events.emit(OnboardingEvent.ShowMessage("已设置本月预算"))
            onDone()
        }
    }

    fun addRecurring(type: Int, amountText: String, categoryId: Long?, cycle: Int, onDone: () -> Unit) {
        val cents = amountText.toCents() ?: run {
            emit("请输入有效金额")
            return
        }
        val catId = categoryId ?: run {
            emit("请选择分类")
            return
        }
        viewModelScope.launch {
            val ledgerId = ledgerRepository.getActiveLedgerId()
            val start = LocalDate.now().toEpochDay()
            recurringRepository.add(
                RecurringBill(
                    type = type,
                    amountCents = cents,
                    categoryId = catId,
                    accountId = Bill.DEFAULT_ACCOUNT_ID,
                    ledgerId = ledgerId,
                    note = "",
                    cycle = cycle,
                    startEpochDay = start,
                    lastGeneratedEpochDay = start,
                    enabled = true
                )
            )
            _events.emit(OnboardingEvent.ShowMessage("已添加周期账单"))
            onDone()
        }
    }

    fun addGoal(name: String, amountText: String, onDone: () -> Unit) {
        val cents = amountText.toCents() ?: run {
            emit("请输入有效目标金额")
            return
        }
        if (name.isBlank()) {
            emit("目标名称不能为空")
            return
        }
        viewModelScope.launch {
            goalRepository.add(
                Goal(
                    name = name.trim(),
                    icon = "🎯",
                    targetCents = cents,
                    deadlineEpochDay = LocalDate.now().plusMonths(6).toEpochDay()
                )
            )
            _events.emit(OnboardingEvent.ShowMessage("已创建存钱目标「${name.trim()}」"))
            onDone()
        }
    }

    fun setPin(pin: String): Boolean {
        if (pin.length !in 4..6 || !pin.all { it.isDigit() }) {
            emit("密码需为 4-6 位数字")
            return false
        }
        viewModelScope.launch { lockSettingsRepository.setPin(pin) }
        return true
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { lockSettingsRepository.setBiometricEnabled(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun emit(message: String) {
        viewModelScope.launch { _events.emit(OnboardingEvent.ShowMessage(message)) }
    }
}
