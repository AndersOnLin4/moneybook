package com.andersonlin.moneybook.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.model.Account
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.data.model.Goal
import com.andersonlin.moneybook.data.model.Ledger
import com.andersonlin.moneybook.data.repository.AccountRepository
import com.andersonlin.moneybook.data.repository.BillRepository
import com.andersonlin.moneybook.data.repository.BudgetRepository
import com.andersonlin.moneybook.data.repository.CategoryRepository
import com.andersonlin.moneybook.data.repository.GoalRepository
import com.andersonlin.moneybook.data.repository.LedgerRepository
import com.andersonlin.moneybook.util.endEpochDay
import com.andersonlin.moneybook.util.startEpochDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class HomeUiState(
    val month: YearMonth = YearMonth.now(),
    val income: Long = 0L,
    val expense: Long = 0L,
    val todayIncome: Long = 0L,
    val todayExpense: Long = 0L,
    val budgetCents: Long? = null,
    val recentBills: List<Bill> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
    val accounts: Map<Long, Account> = emptyMap(),
    val ledgerId: Long = Bill.DEFAULT_LEDGER_ID,
    val ledgers: List<Ledger> = emptyList(),
    val goals: List<Goal> = emptyList()
) {
    val balance: Long get() = income - expense

    /** 当前账本名 */
    val ledgerName: String get() = ledgers.firstOrNull { it.id == ledgerId }?.name ?: "账本"

    /** 预算已超支时返回超支金额，否则 null */
    val overspendCents: Long? get() = budgetCents?.let { (expense - it).takeIf { d -> d > 0 } }
}

/** 首页：当月收支结余 + 今日收支 + 预算进度 + 存钱目标 + 最近账单（按当前账本隔离） */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val billRepository: BillRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
    private val ledgerRepository: LedgerRepository,
    private val goalRepository: GoalRepository
) : ViewModel() {

    private val month = YearMonth.now()

    private data class SummaryData(
        val income: Long,
        val expense: Long,
        val todayIncome: Long,
        val todayExpense: Long
    )

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            ledgerRepository.activeLedgerId,
            ledgerRepository.getAllLedgers()
        ) { id, ledgers -> id to ledgers }
            .flatMapLatest { (ledgerId, ledgers) ->
                combine(
                    combine(
                        billRepository.getMonthSummary(ledgerId, month.startEpochDay(), month.endEpochDay()),
                        billRepository.getDaySummary(ledgerId, LocalDate.now().toEpochDay())
                    ) { monthSums, daySums ->
                        SummaryData(
                            income = monthSums.firstOrNull { it.type == Bill.TYPE_INCOME }?.total ?: 0L,
                            expense = monthSums.firstOrNull { it.type == Bill.TYPE_EXPENSE }?.total ?: 0L,
                            todayIncome = daySums.firstOrNull { it.type == Bill.TYPE_INCOME }?.total ?: 0L,
                            todayExpense = daySums.firstOrNull { it.type == Bill.TYPE_EXPENSE }?.total ?: 0L
                        )
                    },
                    billRepository.getRecentBills(ledgerId, 10),
                    categoryRepository.getAllCategories(),
                    accountRepository.getAllAccounts(),
                    budgetRepository.getAllBudgets()
                ) { summary, bills, cats, accounts, budgets ->
                    HomeUiState(
                        month = month,
                        income = summary.income,
                        expense = summary.expense,
                        todayIncome = summary.todayIncome,
                        todayExpense = summary.todayExpense,
                        budgetCents = budgets
                            .firstOrNull { it.year == month.year && it.month == month.monthValue }
                            ?.amountCents,
                        recentBills = bills,
                        categories = cats.associateBy { it.id },
                        accounts = accounts.associateBy { it.id },
                        ledgerId = ledgerId,
                        ledgers = ledgers
                    )
                }
            },
        goalRepository.getAllGoals()
    ) { state, goals ->
        state.copy(goals = goals)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(month = month)
        )

    /** 切换当前账本（首页、账单、统计、小组件随动） */
    fun setActiveLedger(id: Long) {
        viewModelScope.launch { ledgerRepository.setActiveLedger(id) }
    }
}
