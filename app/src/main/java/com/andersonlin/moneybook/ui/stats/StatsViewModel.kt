package com.andersonlin.moneybook.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.data.repository.BillRepository
import com.andersonlin.moneybook.data.repository.CategoryRepository
import com.andersonlin.moneybook.util.endEpochDay
import com.andersonlin.moneybook.util.startEpochDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth

/** 饼图切片 */
data class StatSlice(
    val category: Category,
    val total: Long
)

data class StatsUiState(
    val month: YearMonth = YearMonth.now(),
    val type: Int = Bill.TYPE_EXPENSE,
    val slices: List<StatSlice> = emptyList(),
    val total: Long = 0L,
    val isCurrentMonth: Boolean = true
)

/** 统计页：月度分类占比，可切换月份与收支类型 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val billRepository: BillRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val type = MutableStateFlow(Bill.TYPE_EXPENSE)

    val uiState: StateFlow<StatsUiState> = combine(
        combine(month, type) { m, t -> m to t }
            .flatMapLatest { (m, t) ->
                billRepository.getCategoryStats(t, m.startEpochDay(), m.endEpochDay())
            },
        categoryRepository.getAllCategories()
    ) { sums, categories ->
        val categoryMap = categories.associateBy { it.id }
        val slices = sums
            .mapNotNull { sum -> categoryMap[sum.categoryId]?.let { StatSlice(it, sum.total) } }
            .sortedByDescending { it.total }
        val currentMonth = month.value
        StatsUiState(
            month = currentMonth,
            type = type.value,
            slices = slices,
            total = slices.sumOf { it.total },
            isCurrentMonth = currentMonth == YearMonth.now()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState()
    )

    fun prevMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() {
        if (month.value < YearMonth.now()) {
            month.update { it.plusMonths(1) }
        }
    }

    fun setType(type: Int) {
        this.type.value = type
    }
}
