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

/** 柱状图一组（某月收支） */
data class BarGroup(
    val label: String,
    val expense: Long,
    val income: Long
)

/** 折线图一点（某月收支） */
data class LinePoint(
    val label: String,
    val expense: Long,
    val income: Long
)

data class StatsUiState(
    val scale: Int = StatsViewModel.SCALE_MONTH,
    val label: String = monthLabel(YearMonth.now()),
    val type: Int = StatsViewModel.TYPE_EXPENSE,
    val chartType: Int = StatsViewModel.CHART_PIE,
    val slices: List<StatSlice> = emptyList(),
    val total: Long = 0L,
    val canGoNext: Boolean = false,
    val barGroups: List<BarGroup> = emptyList(),
    val linePoints: List<LinePoint> = emptyList(),
    val barHasData: Boolean = false,
    val lineHasData: Boolean = false
) {
    val typeLabel: String
        get() = when (type) {
            StatsViewModel.TYPE_INCOME -> "收入"
            StatsViewModel.TYPE_ALL -> "流水"
            else -> "支出"
        }
}

/**
 * 统计页：饼图（分类占比，支持支出/收入/全部三态）+ 近 6 月收支柱状对比 + 近 12 月消费趋势折线。
 * 饼图支持 日 / 周 / 月 / 年 维度切换；柱状与折线按自然月聚合（Canvas 自绘，无第三方图表库）。
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

        const val TYPE_EXPENSE = 0
        const val TYPE_INCOME = 1
        const val TYPE_ALL = -1

        const val CHART_PIE = 0
        const val CHART_BAR = 1
        const val CHART_LINE = 2
    }

    private val scale = MutableStateFlow(SCALE_MONTH)
    private val anchor = MutableStateFlow(LocalDate.now())
    private val type = MutableStateFlow(TYPE_EXPENSE)
    private val chartType = MutableStateFlow(CHART_PIE)

    val uiState: StateFlow<StatsUiState> = combine(
        combine(scale, anchor, type, chartType) { s, a, t, c ->
            StatsQuery(s, a, t, c)
        }.flatMapLatest { q ->
            val range = rangeFor(q.scale, q.anchor)
            // 柱状/折线数据窗口：以锚点所在月为终点往前推 12 个月
            val endMonth = YearMonth.from(q.anchor)
            val startMonth = endMonth.minusMonths(11)
            combine(
                billRepository.getCategoryStats(q.type, range.startDay, range.endDay)
                    .map { Triple(range, q, it) },
                billRepository.getMonthlyTotals(startMonth.startEpochDay(), endMonth.endEpochDay())
                    .map { it }
            ) { pie, monthly -> QueryResult(range, q, pie.third, monthly) }
        },
        categoryRepository.getAllCategories()
    ) { result, categories ->
        val categoryMap = categories.associateBy { it.id }
        val slices = result.sums
            .mapNotNull { sum -> categoryMap[sum.categoryId]?.let { StatSlice(it, sum.total) } }
            .sortedByDescending { it.total }

        val monthlyMap = result.monthly.groupBy { it.ym }
        val endMonth = YearMonth.from(anchor.value)
        val startMonth = endMonth.minusMonths(11)
        val months = (0..11).map { startMonth.plusMonths(it.toLong()) }
        val points = months.map { ym ->
            val rows = monthlyMap[ymKey(ym)] ?: emptyList()
            LinePoint(
                label = "${ym.monthValue}月",
                expense = rows.firstOrNull { it.type == Bill.TYPE_EXPENSE }?.total ?: 0L,
                income = rows.firstOrNull { it.type == Bill.TYPE_INCOME }?.total ?: 0L
            )
        }

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
            label = result.range.label,
            type = result.query.type,
            chartType = result.query.chartType,
            slices = slices,
            total = slices.sumOf { it.total },
            canGoNext = canGoNext,
            barGroups = points.takeLast(6).map { BarGroup(it.label, it.expense, it.income) },
            linePoints = points,
            barHasData = points.takeLast(6).any { it.expense > 0 || it.income > 0 },
            lineHasData = points.any { it.expense > 0 || it.income > 0 }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState()
    )

    fun setScale(scale: Int) {
        this.scale.value = scale
    }

    fun setChartType(chartType: Int) {
        this.chartType.value = chartType
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

private data class StatsQuery(
    val scale: Int,
    val anchor: LocalDate,
    val type: Int,
    val chartType: Int
)

private data class QueryResult(
    val range: StatsRange,
    val query: StatsQuery,
    val sums: List<com.andersonlin.moneybook.data.db.BillDao.CategorySum>,
    val monthly: List<com.andersonlin.moneybook.data.db.BillDao.MonthlyTotal>
)

private fun ymKey(ym: YearMonth): String =
    "%04d-%02d".format(ym.year, ym.monthValue)

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
