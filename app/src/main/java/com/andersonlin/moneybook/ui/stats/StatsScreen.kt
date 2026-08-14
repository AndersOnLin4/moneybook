package com.andersonlin.moneybook.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.andersonlin.moneybook.util.monthLabel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { CenterAlignedTopAppBar(title = { Text("统计") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 日 / 周 / 月 / 年切换
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                listOf(
                    StatsViewModel.SCALE_DAY to "日",
                    StatsViewModel.SCALE_WEEK to "周",
                    StatsViewModel.SCALE_MONTH to "月",
                    StatsViewModel.SCALE_YEAR to "年"
                ).forEachIndexed { index, (value, labelText) ->
                    SegmentedButton(
                        selected = state.scale == value,
                        onClick = { viewModel.setScale(value) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 4),
                        label = { Text(labelText) }
                    )
                }
            }

            // 时间区间切换
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
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = viewModel::next, enabled = state.canGoNext) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "下一个")
                }
            }

            // 支出 / 收入切换
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SegmentedButton(
                    selected = state.type == Bill.TYPE_EXPENSE,
                    onClick = { viewModel.setType(Bill.TYPE_EXPENSE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("支出") }
                )
                SegmentedButton(
                    selected = state.type == Bill.TYPE_INCOME,
                    onClick = { viewModel.setType(Bill.TYPE_INCOME) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("收入") }
                )
            }

            if (state.slices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Filled.PieChart,
                        title = "该时间段暂无${if (state.type == Bill.TYPE_EXPENSE) "支出" else "收入"}记录"
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "chart") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            DonutChart(
                                slices = state.slices,
                                colors = ChartColors,
                                modifier = Modifier.size(220.dp)
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (state.type == Bill.TYPE_EXPENSE) "总支出" else "总收入",
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
                }
            }
        }
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
                text = formatCents(slice.total),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
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
