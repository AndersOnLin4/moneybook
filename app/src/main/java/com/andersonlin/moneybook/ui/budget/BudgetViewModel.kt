package com.andersonlin.moneybook.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.model.Budget
import com.andersonlin.moneybook.data.repository.BudgetRepository
import com.andersonlin.moneybook.util.toCents
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BudgetEvent {
    data class ShowMessage(val message: String) : BudgetEvent
}

/** 月度预算：按「年-月」设置总支出预算，可删除 */
class BudgetViewModel(
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    val budgets: StateFlow<List<Budget>> = budgetRepository.getAllBudgets()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _events = MutableSharedFlow<BudgetEvent>()
    val events = _events.asSharedFlow()

    /** 保存某月预算；金额为 0 或非法时清除该月预算 */
    fun setBudget(year: Int, month: Int, amountText: String) {
        val cents = amountText.toCents()
        if (cents == null) {
            emit(BudgetEvent.ShowMessage("请输入有效金额（大于 0，最多两位小数）"))
            return
        }
        viewModelScope.launch {
            budgetRepository.setBudget(year, month, cents)
            _events.emit(BudgetEvent.ShowMessage("已设置 ${year}年${month}月 预算"))
        }
    }

    fun clearBudget(year: Int, month: Int) {
        viewModelScope.launch {
            budgetRepository.setBudget(year, month, 0L)
            _events.emit(BudgetEvent.ShowMessage("已清除该月预算"))
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch {
            budgetRepository.deleteBudget(budget)
            _events.emit(BudgetEvent.ShowMessage("已删除 ${budget.year}年${budget.month}月 预算"))
        }
    }

    private fun emit(event: BudgetEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}
