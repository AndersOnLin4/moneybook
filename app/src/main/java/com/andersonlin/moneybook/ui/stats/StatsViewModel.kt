package com.andersonlin.moneybook.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.data.repository.BillRepository
import com.andersonlin.moneybook.data.repository.CategoryRepository
import com.andersonlin.moneybook.util.endEpochDay
import com.andersonlin.moneybook.util.fullDateLabel
import com.andersonlin.moneybook.util.monthLabel
import com.andersonlin.moneybook.util.startEpochDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** 饼图切片 */
data class StatSlice(
    val category: Category,
    val total: Long
)

/** 统计时间范围 */
data class StatsRange(
    val label: String,
    val startDay: Long,
    val endDay: Long
)

data class StatsUiState(
    val scale: Int = StatsViewModel.SCALE_MONTH,
    val label: String = monthLabel(YearMonth.now()),
    val type: Int = Bill.TYPE_EXPENSE,
    val slices: List<StatSlice> = emptyList(),
    val total: Long = 0L,
    val canGoNext: Boolean = false
)

/**
 * 统计页：分类占比饼图，支持 日 / 周 / 月 / 年 四种时间维度与收支类型切换。
 * 周按周一至周日计算。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val billRepository: BillRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    companion object {
        const val SCALE_DAY = 0
        const val SCALE_WEEK = 1
        const val SCALE_MONTH = 2
        const val SCALE_YEAR = 3
    }

    private val scale = MutableStateFlow(SCALE_MONTH)
    private val anchor = MutableStateFlow(LocalDate.now())
    private val type = MutableStateFlow(Bill.TYPE_EXPENSE)

    val uiState: StateFlow<StatsUiState> = combine(
        combine(scale, anchor, type) { s, a, t -> Triple(s, a, t) }
            .flatMapLatest { (s, a, t) ->
                val range = rangeFor(s, a)
                billRepository.getCategoryStats(t, range.startDay, range.endDay)
                    .map { Triple(range, t, it) }
            },
        categoryRepository.getAllCategories()
    ) { (range, currentType, sums), categories ->
        val categoryMap = categories.associateBy { it.id }
        val slices = sums
            .mapNotNull { sum -> categoryMap[sum.categoryId]?.let { StatSlice(it, sum.total) } }
            .sortedByDescending { it.total }
        val today = LocalDate.now()
        val canGoNext = when (scale.value) {
            SCALE_DAY -> anchor.value < today
            SCALE_WEEK -> anchor.value
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusDays(6) < today
            SCALE_MONTH -> YearMonth.from(anchor.value) < YearMonth.now()
            SCALE_YEAR -> anchor.value.year < today.year
            else -> false
        }
        StatsUiState(
            scale = scale.value,
            label = range.label,
            type = currentType,
            slices = slices,
            total = slices.sumOf { it.total },
            canGoNext = canGoNext
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState()
    )

    fun setScale(scale: Int) {
        this.scale.value = scale
    }

    fun prev() = move(-1)

    fun next() {
        if (uiState.value.canGoNext) move(1)
    }

    fun setType(type: Int) {
        this.type.value = type
    }

    private fun move(delta: Int) {
        val a = anchor.value
        anchor.value = when (scale.value) {
            SCALE_DAY -> a.plusDays(delta.toLong())
            SCALE_WEEK -> a.plusWeeks(delta.toLong())
            SCALE_MONTH -> a.plusMonths(delta.toLong())
            SCALE_YEAR -> a.plusYears(delta.toLong())
            else -> a
        }
    }
}

/** 根据维度与锚点日期计算统计区间（闭区间 epochDay） */
private fun rangeFor(scale: Int, anchor: LocalDate): StatsRange = when (scale) {
    StatsViewModel.SCALE_DAY -> StatsRange(
        label = fullDateLabel(anchor.toEpochDay()),
        startDay = anchor.toEpochDay(),
        endDay = anchor.toEpochDay()
    )
    StatsViewModel.SCALE_WEEK -> {
        val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        StatsRange(
            label = "${monday.monthValue}月${monday.dayOfMonth}日 - " +
                "${sunday.monthValue}月${sunday.dayOfMonth}日",
            startDay = monday.toEpochDay(),
            endDay = sunday.toEpochDay()
        )
    }
    StatsViewModel.SCALE_MONTH -> {
        val ym = YearMonth.from(anchor)
        StatsRange(
            label = monthLabel(ym),
            startDay = ym.startEpochDay(),
            endDay = ym.endEpochDay()
        )
    }
    else -> {
        val year = anchor.year
        StatsRange(
            label = "${year}年",
            startDay = LocalDate.of(year, 1, 1).toEpochDay(),
            endDay = LocalDate.of(year, 12, 31).toEpochDay()
        )
    }
}
