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
import com.andersonlin.moneybook.data.repository.CategoryBudgetRepository
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

/** 分类预算提醒 */
data class CategoryAlert(
    val name: String,
    val icon: String,
    val usedCents: Long,
    val budgetCents: Long
) {
    val over: Boolean get() = usedCents >= budgetCents
}

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
    val goals: List<Goal> = emptyList(),
    val categoryAlerts: List<CategoryAlert> = emptyList()
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
    private val goalRepository: GoalRepository,
    private val categoryBudgetRepository: CategoryBudgetRepository
) : ViewModel() {

    private val month = YearMonth.now()

    private data class SummaryData(
        val income: Long,
        val expense: Long,
        val todayIncome: Long,
        val todayExpense: Long
    )

    private data class BaseData(
        val summary: SummaryData,
        val bills: List<Bill>,
        val cats: List<Category>,
        val accounts: List<Account>,
        val budgets: List<com.andersonlin.moneybook.data.model.Budget>
    )

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            ledgerRepository.activeLedgerId,
            ledgerRepository.getAllLedgers()
        ) { id, ledgers -> id to ledgers }
            .flatMapLatest { (ledgerId, ledgers) ->
                combine(
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
                        BaseData(summary, bills, cats, accounts, budgets)
                    },
                    combine(
                        categoryBudgetRepository.getAllBudgets(),
                        billRepository.getCategoryStats(
                            ledgerId,
                            Bill.TYPE_EXPENSE,
                            month.startEpochDay(),
                            month.endEpochDay()
                        )
                    ) { categoryBudgets, categoryStats ->
                        categoryBudgets to categoryStats
                    }
                ) { base, (categoryBudgets, categoryStats) ->
                    val categoryMap = base.cats.associateBy { it.id }
                    val alerts = categoryBudgets
                        .filter { it.year == month.year && it.month == month.monthValue }
                        .mapNotNull { cb ->
                            val category = categoryMap[cb.categoryId] ?: return@mapNotNull null
                            val used = categoryStats
                                .firstOrNull { it.categoryId == cb.categoryId }?.total ?: 0L
                            if (used >= cb.amountCents * 0.8) {
                                CategoryAlert(category.name, category.icon, used, cb.amountCents)
                            } else {
                                null
                            }
                        }
                        .sortedByDescending { it.usedCents - it.budgetCents }
                    HomeUiState(
                        month = month,
                        income = base.summary.income,
                        expense = base.summary.expense,
                        todayIncome = base.summary.todayIncome,
                        todayExpense = base.summary.todayExpense,
                        budgetCents = base.budgets
                            .firstOrNull { it.year == month.year && it.month == month.monthValue }
                            ?.amountCents,
                        recentBills = base.bills,
                        categories = categoryMap,
                        accounts = base.accounts.associateBy { it.id },
                        ledgerId = ledgerId,
                        ledgers = ledgers,
                        categoryAlerts = alerts.take(3)
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
