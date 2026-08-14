package com.andersonlin.moneybook.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.ui.AppViewModelProvider
import com.andersonlin.moneybook.ui.components.EmptyState
import com.andersonlin.moneybook.ui.theme.ChartColors
import com.andersonlin.moneybook.util.formatCents
import com.andersonlin.moneybook.util.fullDateLabel
import com.andersonlin.moneybook.util.monthLabel
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val expenseColor = MaterialTheme.colorScheme.error
    val incomeColor = MaterialTheme.colorScheme.primary

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { CenterAlignedTopAppBar(title = { Text("统计") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 图表类型：分类占比 / 收支对比 / 趋势 / 日历
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                listOf(
                    StatsViewModel.CHART_PIE to "占比",
                    StatsViewModel.CHART_BAR to "对比",
                    StatsViewModel.CHART_LINE to "趋势",
                    StatsViewModel.CHART_CALENDAR to "日历"
                ).forEachIndexed { index, (value, labelText) ->
                    SegmentedButton(
                        selected = state.chartType == value,
                        onClick = { viewModel.setChartType(value) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 4),
                        label = { Text(labelText) }
                    )
                }
            }

            // 类型：饼图与日历使用（柱状/趋势固定展示收支双系列）
            if (state.chartType == StatsViewModel.CHART_PIE ||
                state.chartType == StatsViewModel.CHART_CALENDAR
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    listOf(
                        StatsViewModel.TYPE_EXPENSE to "支出",
                        StatsViewModel.TYPE_INCOME to "收入",
                        StatsViewModel.TYPE_ALL to "全部"
                    ).forEachIndexed { index, (value, labelText) ->
                        SegmentedButton(
                            selected = state.type == value,
                            onClick = { viewModel.setType(value) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                            label = { Text(labelText) }
                        )
                    }
                }
            }

            // 时间维度：饼图/柱状/趋势显示（日历自带月导航）
            if (state.chartType != StatsViewModel.CHART_CALENDAR) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = viewModel::prev) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "上一个")
                    }
                    Text(
                        text = state.label,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = viewModel::next, enabled = state.canGoNext) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "下一个")
                    }
                }
            }

            when (state.chartType) {
                StatsViewModel.CHART_PIE -> PieContent(state)
                StatsViewModel.CHART_BAR -> BarContent(state, expenseColor, incomeColor)
                StatsViewModel.CHART_CALENDAR -> CalendarContent(state, viewModel, expenseColor, incomeColor)
                else -> LineContent(state, expenseColor, incomeColor)
            }
        }
    }
}

@Composable
private fun ColumnScope.PieContent(state: StatsUiState) {
    if (state.slices.isEmpty()) {
        EmptyBox("该时间段暂无${state.typeLabel}记录")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "chart") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                DonutChart(
                    slices = state.slices,
                    colors = ChartColors,
                    modifier = Modifier.size(210.dp)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (state.type == StatsViewModel.TYPE_ALL) "总流水" else "总${state.typeLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatCents(state.total),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        item(key = "legend_header") {
            Text(
                text = "分类明细",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        items(state.slices, key = { it.category.id }) { slice ->
            StatLegendRow(
                slice = slice,
                color = ChartColors[state.slices.indexOf(slice) % ChartColors.size],
                total = state.total
            )
        }
        item(key = "bottom_space") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ColumnScope.BarContent(state: StatsUiState, expenseColor: Color, incomeColor: Color) {
    if (!state.chartHasData) {
        EmptyBox("该时间段暂无收支记录")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "title") {
            Text(
                text = state.chartWindowLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item(key = "chart") {
            Column {
                BarChart(
                    points = state.chartPoints,
                    expenseColor = expenseColor,
                    incomeColor = incomeColor,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                ChartLegend(expenseColor, incomeColor)
            }
        }
        items(state.chartPoints.reversed(), key = { "detail_" + it.label }) { point ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "-" + formatCents(point.expense),
                    style = MaterialTheme.typography.bodyMedium,
                    color = expenseColor
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "+" + formatCents(point.income),
                    style = MaterialTheme.typography.bodyMedium,
                    color = incomeColor
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.LineContent(state: StatsUiState, expenseColor: Color, incomeColor: Color) {
    if (!state.chartHasData) {
        EmptyBox("该时间段暂无收支记录")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "title") {
            Text(
                text = state.chartWindowLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item(key = "chart") {
            Column {
                LineChart(
                    points = state.chartPoints,
                    expenseColor = expenseColor,
                    incomeColor = incomeColor,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                ChartLegend(expenseColor, incomeColor)
            }
        }
        item(key = "bottom_space") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ColumnScope.CalendarContent(
    state: StatsUiState,
    viewModel: StatsViewModel,
    expenseColor: Color,
    incomeColor: Color
) {
    var selectedDay by remember { mutableStateOf<CalendarDay?>(null) }
    val circleColor = when (state.type) {
        StatsViewModel.TYPE_INCOME -> incomeColor
        StatsViewModel.TYPE_ALL -> MaterialTheme.colorScheme.tertiary
        else -> expenseColor
    }
    val dayValue: (CalendarDay) -> Long = { day ->
        when (state.type) {
            StatsViewModel.TYPE_INCOME -> day.income
            StatsViewModel.TYPE_ALL -> day.expense + day.income
            else -> day.expense
        }
    }
    val maxValue = state.calendarDays.maxOfOrNull(dayValue)?.coerceAtLeast(1L) ?: 1L
    val today = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // 月导航
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = viewModel::prev) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
            }
            Text(
                text = monthLabel(state.calendarMonth),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = viewModel::next, enabled = state.canGoNext) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "下个月")
            }
        }
        // 图例说明
        Text(
            text = "圆圈大小表示当天金额",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        // 星期头（周一为首）
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // 日历网格
        val firstOffset = state.calendarDays.firstOrNull()
            ?.date?.dayOfWeek?.value?.minus(1) ?: 0
        val cells: List<CalendarDay?> =
            List(firstOffset) { null } + state.calendarDays
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { index ->
                    val day = week.getOrNull(index)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clickable(enabled = day != null) {
                                day?.let { selectedDay = it }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            val value = dayValue(day)
                            val radius = if (value > 0) {
                                (5f + 15f * kotlin.math.sqrt(value.toFloat() / maxValue)).dp
                            } else {
                                0.dp
                            }
                            if (radius > 0.dp) {
                                Box(
                                    modifier = Modifier
                                        .size(radius * 2)
                                        .clip(CircleShape)
                                        .background(circleColor.copy(alpha = 0.35f))
                                )
                            }
                            Text(
                                text = day.date.dayOfMonth.toString(),
                                fontSize = 12.sp,
                                fontWeight = if (value > 0) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    day.date == today -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    selectedDay?.let { day ->
        AlertDialog(
            onDismissRequest = { selectedDay = null },
            title = { Text(fullDateLabel(day.date.toEpochDay())) },
            text = {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("支出", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            text = "-" + formatCents(day.expense),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = expenseColor
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("收入", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            text = "+" + formatCents(day.income),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = incomeColor
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDay = null }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun ChartLegend(expenseColor: Color, incomeColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(expenseColor)
        )
        Spacer(Modifier.width(6.dp))
        Text("支出", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(20.dp))
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(incomeColor)
        )
        Spacer(Modifier.width(6.dp))
        Text("收入", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ColumnScope.EmptyBox(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentAlignment = Alignment.Center
    ) {
        EmptyState(icon = Icons.Filled.PieChart, title = title)
    }
}

@Composable
private fun StatLegendRow(slice: StatSlice, color: Color, total: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${slice.category.icon} ${slice.category.name}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = (if (slice.category.type == Bill.TYPE_INCOME) "+" else "-") +
                    formatCents(slice.total),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (slice.category.type == Bill.TYPE_INCOME) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            Text(
                text = if (total > 0) {
                    String.format(Locale.US, "%.1f%%", slice.total * 100.0 / total)
                } else {
                    "0%"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
