package com.andersonlin.moneybook.ui.bill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.model.Account
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.data.repository.AccountRepository
import com.andersonlin.moneybook.data.repository.BillRepository
import com.andersonlin.moneybook.data.repository.CategoryRepository
import com.andersonlin.moneybook.data.repository.LedgerRepository
import com.andersonlin.moneybook.util.toYearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth

/** 列表筛选条件 */
data class BillFilter(
    val type: Int = TYPE_ALL,
    val keyword: String = "",
    val minCents: Long? = null,
    val maxCents: Long? = null
) {
    val hasAmountFilter: Boolean get() = minCents != null || maxCents != null

    companion object {
        const val TYPE_ALL = -1
    }
}

/** 列表行：月份头 或 账单 */
sealed interface BillListRow {
    data class Header(
        val month: YearMonth,
        val expense: Long,
        val income: Long
    ) : BillListRow

    data class Item(
        val bill: Bill,
        val category: Category?,
        val account: Account?
    ) : BillListRow
}

data class BillListUiState(
    val items: List<BillListRow> = emptyList(),
    val typeFilter: Int = BillFilter.TYPE_ALL,
    val keyword: String = "",
    val minCents: Long? = null,
    val maxCents: Long? = null
) {
    val hasAnyFilter: Boolean
        get() = keyword.isNotBlank() || typeFilter != BillFilter.TYPE_ALL ||
            minCents != null || maxCents != null
}

/** 账单列表：时间倒序、按月分组、搜索筛选 */
@OptIn(ExperimentalCoroutinesApi::class)
class BillListViewModel(
    private val billRepository: BillRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val ledgerRepository: LedgerRepository
) : ViewModel() {

    private val filter = MutableStateFlow(BillFilter())

    val uiState: StateFlow<BillListUiState> = combine(filter, ledgerRepository.activeLedgerId) { f, id ->
        f to id
    }.flatMapLatest { (f, ledgerId) ->
        combine(
            billRepository.searchBills(ledgerId, f.type, f.keyword.trim(), f.minCents, f.maxCents),
            categoryRepository.getAllCategories(),
            accountRepository.getAllAccounts()
        ) { bills, cats, accounts ->
            BillListUiState(
                items = groupBills(bills, cats, accounts),
                typeFilter = f.type,
                keyword = f.keyword,
                minCents = f.minCents,
                maxCents = f.maxCents
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BillListUiState()
    )

    fun setKeyword(keyword: String) = filter.update { it.copy(keyword = keyword) }

    fun setTypeFilter(type: Int) = filter.update { it.copy(type = type) }

    /** 金额区间（分）；两者都为 null 表示清除 */
    fun setAmountRange(minCents: Long?, maxCents: Long?) =
        filter.update { it.copy(minCents = minCents, maxCents = maxCents) }

    fun clearAllFilters() = filter.update {
        it.copy(type = BillFilter.TYPE_ALL, keyword = "", minCents = null, maxCents = null)
    }
}

/** 将时间倒序的账单按月份分组，组头附带当月收支合计 */
private fun groupBills(
    bills: List<Bill>,
    categories: List<Category>,
    accounts: List<Account>
): List<BillListRow> {
    val categoryMap = categories.associateBy { it.id }
    val accountMap = accounts.associateBy { it.id }
    val result = mutableListOf<BillListRow>()
    var i = 0
    while (i < bills.size) {
        val month = bills[i].dateEpochDay.toYearMonth()
        var expense = 0L
        var income = 0L
        val monthBills = mutableListOf<Bill>()
        while (i < bills.size && bills[i].dateEpochDay.toYearMonth() == month) {
            val bill = bills[i]
            monthBills.add(bill)
            if (bill.type == Bill.TYPE_EXPENSE) expense += bill.amountCents else income += bill.amountCents
            i++
        }
        result.add(BillListRow.Header(month, expense, income))
        monthBills.forEach { bill ->
            result.add(BillListRow.Item(bill, categoryMap[bill.categoryId], accountMap[bill.accountId]))
        }
    }
    return result
}
