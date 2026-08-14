package com.andersonlin.moneybook.ui.stats

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.ui.AppViewModelProvider
import com.andersonlin.moneybook.ui.components.EmptyState
import com.andersonlin.moneybook.ui.theme.ChartColors
import com.andersonlin.moneybook.util.formatCents
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
            // 图表类型：分类占比 / 收支对比 / 趋势
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                listOf(
                    StatsViewModel.CHART_PIE to "分类占比",
                    StatsViewModel.CHART_BAR to "收支对比",
                    StatsViewModel.CHART_LINE to "趋势"
                ).forEachIndexed { index, (value, labelText) ->
                    SegmentedButton(
                        selected = state.chartType == value,
                        onClick = { viewModel.setChartType(value) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                        label = { Text(labelText) }
                    )
                }
            }

            // 类型：仅饼图使用（柱状/趋势固定展示收支双系列）
            if (state.chartType == StatsViewModel.CHART_PIE) {
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

            // 时间维度：始终显示，对三种图表都生效
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

            when (state.chartType) {
                StatsViewModel.CHART_PIE -> PieContent(state)
                StatsViewModel.CHART_BAR -> BarContent(state, expenseColor, incomeColor)
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
