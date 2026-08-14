package com.andersonlin.moneybook.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.model.Account
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.data.repository.AccountRepository
import com.andersonlin.moneybook.data.repository.BillRepository
import com.andersonlin.moneybook.data.repository.BudgetRepository
import com.andersonlin.moneybook.data.repository.CategoryRepository
import com.andersonlin.moneybook.util.endEpochDay
import com.andersonlin.moneybook.util.startEpochDay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class HomeUiState(
    val month: YearMonth = YearMonth.now(),
    val income: Long = 0L,
    val expense: Long = 0L,
    val budgetCents: Long? = null,
    val recentBills: List<Bill> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
    val accounts: Map<Long, Account> = emptyMap()
) {
    val balance: Long get() = income - expense

    /** 预算已超支时返回超支金额，否则 null */
    val overspendCents: Long? get() = budgetCents?.let { (expense - it).takeIf { d -> d > 0 } }
}

/** 首页：当月收支结余 + 预算进度 + 最近账单 */
class HomeViewModel(
    private val billRepository: BillRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    private val month = YearMonth.now()

    val uiState: StateFlow<HomeUiState> = combine(
        billRepository.getMonthSummary(month.startEpochDay(), month.endEpochDay()),
        billRepository.getRecentBills(10),
        categoryRepository.getAllCategories(),
        accountRepository.getAllAccounts(),
        budgetRepository.getAllBudgets()
    ) { sums, bills, cats, accounts, budgets ->
        HomeUiState(
            month = month,
            income = sums.firstOrNull { it.type == Bill.TYPE_INCOME }?.total ?: 0L,
            expense = sums.firstOrNull { it.type == Bill.TYPE_EXPENSE }?.total ?: 0L,
            budgetCents = budgets
                .firstOrNull { it.year == month.year && it.month == month.monthValue }
                ?.amountCents,
            recentBills = bills,
            categories = cats.associateBy { it.id },
            accounts = accounts.associateBy { it.id }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(month = month)
    )
}
