package com.andersonlin.moneybook.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.data.repository.BillRepository
import com.andersonlin.moneybook.data.repository.CategoryRepository
import com.andersonlin.moneybook.data.repository.LedgerRepository
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

/** 柱状/折线共用的一组数据点 */
data class ChartPoint(
    val label: String,
    val expense: Long,
    val income: Long
)

/** 日历视图的一天 */
data class CalendarDay(
    val date: LocalDate,
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
    val chartPoints: List<ChartPoint> = emptyList(),
    val chartWindowLabel: String = "",
    val chartHasData: Boolean = false,
    val calendarMonth: YearMonth = YearMonth.now(),
    val calendarDays: List<CalendarDay> = emptyList(),
    val prevTotal: Long? = null
) {
    val typeLabel: String
        get() = when (type) {
            StatsViewModel.TYPE_INCOME -> "收入"
            StatsViewModel.TYPE_ALL -> "流水"
            else -> "支出"
        }
}

/**
 * 统计页：分类占比饼图（支出/收入/全部三态）+ 收支对比图。
 * 日/周/月/年维度始终生效：
 * - 饼图：区间内分类占比
 * - 柱状/趋势：同一窗口数据 —— 年 = 该年 12 个月，月 = 该月每日，周 = 该周 7 日，日 = 近 7 天
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val billRepository: BillRepository,
    private val categoryRepository: CategoryRepository,
    private val ledgerRepository: LedgerRepository
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
        const val CHART_CALENDAR = 3
    }

    private val scale = MutableStateFlow(SCALE_MONTH)
    private val anchor = MutableStateFlow(LocalDate.now())
    private val type = MutableStateFlow(TYPE_EXPENSE)
    private val chartType = MutableStateFlow(CHART_PIE)

    val uiState: StateFlow<StatsUiState> = combine(
        combine(scale, anchor, type, chartType, ledgerRepository.activeLedgerId) { s, a, t, c, l ->
            StatsQuery(s, a, t, c, l)
        }
            .flatMapLatest { q ->
                val range = rangeFor(q.scale, q.anchor)
                // 图表窗口（柱状/趋势共用）：年维度用逐月聚合，其余用逐日聚合
                val year = q.anchor.year
                val (prevStart, prevEnd) = prevRangeFor(q.scale, q.chartType, q.anchor)
                combine(
                    billRepository.getCategoryStats(q.ledgerId, q.type, range.startDay, range.endDay),
                    billRepository.getMonthlyTotals(
                        q.ledgerId,
                        LocalDate.of(year, 1, 1).toEpochDay(),
                        LocalDate.of(year, 12, 31).toEpochDay()
                    ),
                    billRepository.getDailyTotals(q.ledgerId, dailyStart(q), dailyEnd(q)),
                    billRepository.getMonthSummary(q.ledgerId, prevStart, prevEnd)
                ) { pie, monthly, daily, prevSums ->
                    QueryResult(range, q, pie, monthly, daily, prevSums)
                }
            },
        categoryRepository.getAllCategories()
    ) { result, categories ->
        val categoryMap = categories.associateBy { it.id }
        val slices = result.sums
            .mapNotNull { sum -> categoryMap[sum.categoryId]?.let { StatSlice(it, sum.total) } }
            .sortedByDescending { it.total }

        val points = buildChartPoints(result)

        val today = LocalDate.now()
        val canGoNext = if (chartType.value == CHART_CALENDAR) {
            YearMonth.from(anchor.value) < YearMonth.now()
        } else {
            when (scale.value) {
                SCALE_DAY -> anchor.value < today
                SCALE_WEEK -> anchor.value
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .plusDays(6) < today
                SCALE_MONTH -> YearMonth.from(anchor.value) < YearMonth.now()
                SCALE_YEAR -> anchor.value.year < today.year
                else -> false
            }
        }

        val prevTotal = when (result.query.type) {
            TYPE_INCOME -> result.prevSums.firstOrNull { it.type == Bill.TYPE_INCOME }?.total
            TYPE_EXPENSE -> result.prevSums.firstOrNull { it.type == Bill.TYPE_EXPENSE }?.total
            else -> result.prevSums.sumOf { it.total }.takeIf { result.prevSums.isNotEmpty() }
        }

        StatsUiState(
            scale = scale.value,
            label = result.range.label,
            type = result.query.type,
            chartType = result.query.chartType,
            slices = slices,
            total = slices.sumOf { it.total },
            canGoNext = canGoNext,
            chartPoints = points,
            chartWindowLabel = chartWindowLabel(result.query),
            chartHasData = points.any { it.expense > 0 || it.income > 0 },
            calendarMonth = YearMonth.from(anchor.value),
            calendarDays = buildCalendarDays(result),
            prevTotal = prevTotal
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

    // ---- 图表窗口 ----

    /** 上一周期区间（同比环比用）：日→昨日，周→上周，月→上月，年→去年，日历→上月 */
    private fun prevRangeFor(scale: Int, chartType: Int, anchor: LocalDate): Pair<Long, Long> {
        return when {
            chartType == CHART_CALENDAR ->
                YearMonth.from(anchor).minusMonths(1).let { it.startEpochDay() to it.endEpochDay() }
            scale == SCALE_DAY ->
                anchor.minusDays(1).toEpochDay() to anchor.minusDays(1).toEpochDay()
            scale == SCALE_WEEK -> {
                val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
                monday.toEpochDay() to monday.plusDays(6).toEpochDay()
            }
            scale == SCALE_MONTH ->
                YearMonth.from(anchor).minusMonths(1).let { it.startEpochDay() to it.endEpochDay() }
            else -> {
                val y = anchor.year - 1
                LocalDate.of(y, 1, 1).toEpochDay() to LocalDate.of(y, 12, 31).toEpochDay()
            }
        }
    }

    private fun dailyStart(q: StatsQuery): Long = when {
        q.chartType == CHART_CALENDAR -> YearMonth.from(q.anchor).startEpochDay()
        q.scale == SCALE_DAY -> q.anchor.minusDays(6).toEpochDay()
        q.scale == SCALE_WEEK -> q.anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
        q.scale == SCALE_MONTH -> YearMonth.from(q.anchor).startEpochDay()
        else -> q.anchor.toEpochDay() // 年维度不使用 daily 数据
    }

    private fun dailyEnd(q: StatsQuery): Long = when {
        q.chartType == CHART_CALENDAR -> YearMonth.from(q.anchor).endEpochDay()
        q.scale == SCALE_DAY -> q.anchor.toEpochDay()
        q.scale == SCALE_WEEK -> q.anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusDays(6).toEpochDay()
        q.scale == SCALE_MONTH -> YearMonth.from(q.anchor).endEpochDay()
        else -> q.anchor.toEpochDay()
    }

    /** 日历视图数据：锚点所在月的每日收支 */
    private fun buildCalendarDays(result: QueryResult): List<CalendarDay> {
        val ym = YearMonth.from(result.query.anchor)
        val dailyMap = result.daily.groupBy { it.day }
        return (1..ym.lengthOfMonth()).map { d ->
            val rows = dailyMap[dayKey(ym.year, ym.monthValue, d)] ?: emptyList()
            CalendarDay(
                date = LocalDate.of(ym.year, ym.monthValue, d),
                expense = rows.firstOrNull { it.type == Bill.TYPE_EXPENSE }?.total ?: 0L,
                income = rows.firstOrNull { it.type == Bill.TYPE_INCOME }?.total ?: 0L
            )
        }
    }

    /** 生成柱状/趋势共用的数据点（缺日/缺月补 0） */
    private fun buildChartPoints(result: QueryResult): List<ChartPoint> {
        val q = result.query
        return when (q.scale) {
            SCALE_YEAR -> {
                val monthlyMap = result.monthly.groupBy { it.ym }
                (1..12).map { m ->
                    val rows = monthlyMap[ymKey(q.anchor.year, m)] ?: emptyList()
                    ChartPoint(
                        label = "${m}月",
                        expense = rows.firstOrNull { it.type == Bill.TYPE_EXPENSE }?.total ?: 0L,
                        income = rows.firstOrNull { it.type == Bill.TYPE_INCOME }?.total ?: 0L
                    )
                }
            }
            SCALE_MONTH -> {
                val dailyMap = result.daily.groupBy { it.day }
                val ym = YearMonth.from(q.anchor)
                (1..ym.lengthOfMonth()).map { d ->
                    val rows = dailyMap[dayKey(ym.year, ym.monthValue, d)] ?: emptyList()
                    ChartPoint(
                        label = "${d}日",
                        expense = rows.firstOrNull { it.type == Bill.TYPE_EXPENSE }?.total ?: 0L,
                        income = rows.firstOrNull { it.type == Bill.TYPE_INCOME }?.total ?: 0L
                    )
                }
            }
            else -> {
                val dailyMap = result.daily.groupBy { it.day }
                val start = LocalDate.ofEpochDay(dailyStart(q))
                val days = (dailyEnd(q) - dailyStart(q)).toInt()
                (0..days).map { offset ->
                    val date = start.plusDays(offset.toLong())
                    val rows = dailyMap[dayKey(date.year, date.monthValue, date.dayOfMonth)] ?: emptyList()
                    ChartPoint(
                        label = "${date.monthValue}/${date.dayOfMonth}",
                        expense = rows.firstOrNull { it.type == Bill.TYPE_EXPENSE }?.total ?: 0L,
                        income = rows.firstOrNull { it.type == Bill.TYPE_INCOME }?.total ?: 0L
                    )
                }
            }
        }
    }

    private fun chartWindowLabel(q: StatsQuery): String = when (q.scale) {
        SCALE_YEAR -> "${q.anchor.year}年 · 月度对比"
        SCALE_MONTH -> monthLabel(YearMonth.from(q.anchor)) + " · 每日对比"
        SCALE_WEEK -> {
            val monday = q.anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val sunday = monday.plusDays(6)
            "${monday.monthValue}/${monday.dayOfMonth}-${sunday.monthValue}/${sunday.dayOfMonth} · 每日对比"
        }
        else -> "近 7 天 · 每日对比"
    }
}

private data class StatsQuery(
    val scale: Int,
    val anchor: LocalDate,
    val type: Int,
    val chartType: Int,
    val ledgerId: Long
)

private data class QueryResult(
    val range: StatsRange,
    val query: StatsQuery,
    val sums: List<com.andersonlin.moneybook.data.db.BillDao.CategorySum>,
    val monthly: List<com.andersonlin.moneybook.data.db.BillDao.MonthlyTotal>,
    val daily: List<com.andersonlin.moneybook.data.db.BillDao.DailyTotal>,
    val prevSums: List<com.andersonlin.moneybook.data.db.BillDao.MonthSum>
)

private fun ymKey(year: Int, month: Int): String = "%04d-%02d".format(year, month)

private fun dayKey(year: Int, month: Int, day: Int): String =
    "%04d-%02d-%02d".format(year, month, day)

/** 根据维度与锚点日期计算饼图统计区间（闭区间 epochDay） */
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
