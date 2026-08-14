package com.andersonlin.moneybook.ui.categorybudget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.data.repository.BillRepository
import com.andersonlin.moneybook.data.repository.CategoryBudgetRepository
import com.andersonlin.moneybook.data.repository.CategoryRepository
import com.andersonlin.moneybook.data.repository.LedgerRepository
import com.andersonlin.moneybook.util.endEpochDay
import com.andersonlin.moneybook.util.startEpochDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

data class CategoryBudgetUiState(
    val month: YearMonth = YearMonth.now(),
    val expenseCategories: List<Category> = emptyList(),
    val budgetsForMonth: Map<Long, com.andersonlin.moneybook.data.model.CategoryBudget> = emptyMap(),
    val monthExpenses: Map<Long, Long> = emptyMap(),
    val canGoNext: Boolean = false
)

sealed interface CategoryBudgetEvent {
    data class ShowMessage(val message: String) : CategoryBudgetEvent
}

/** 分类独立预算：每个支出分类按月设限额，显示当月已用/剩余/超支进度 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryBudgetViewModel(
    private val categoryRepository: CategoryRepository,
    private val categoryBudgetRepository: CategoryBudgetRepository,
    private val billRepository: BillRepository,
    private val ledgerRepository: LedgerRepository
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<CategoryBudgetUiState> = combine(
        ledgerRepository.activeLedgerId,
        month
    ) { id, m -> id to m }
        .flatMapLatest { (ledgerId, m) ->
            combine(
                categoryRepository.getAllCategories(),
                categoryBudgetRepository.getAllBudgets(),
                billRepository.getCategoryStats(
                    ledgerId,
                    Bill.TYPE_EXPENSE,
                    m.startEpochDay(),
                    m.endEpochDay()
                )
            ) { cats, budgets, stats ->
                CategoryBudgetUiState(
                    month = m,
                    expenseCategories = cats.filter { it.type == Bill.TYPE_EXPENSE },
                    budgetsForMonth = budgets
                        .filter { it.year == m.year && it.month == m.monthValue }
                        .associateBy { it.categoryId },
                    monthExpenses = stats.associate { it.categoryId to it.total },
                    canGoNext = m < YearMonth.now()
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoryBudgetUiState()
        )

    private val _events = MutableSharedFlow<CategoryBudgetEvent>()
    val events = _events.asSharedFlow()

    fun prevMonth() = month.value.let { month.value = it.minusMonths(1) }

    fun nextMonth() {
        if (month.value < YearMonth.now()) {
            month.value = month.value.plusMonths(1)
        }
    }

    fun setBudget(categoryId: Long, cents: Long) {
        val m = month.value
        viewModelScope.launch {
            categoryBudgetRepository.setBudget(categoryId, m.year, m.monthValue, cents)
            _events.emit(
                CategoryBudgetEvent.ShowMessage(
                    if (cents > 0) "已设置 ${m.monthValue}月分类预算" else "已清除该分类预算"
                )
            )
        }
    }

    fun emit(message: String) {
        viewModelScope.launch { _events.emit(CategoryBudgetEvent.ShowMessage(message)) }
    }
}
