package com.andersonlin.moneybook.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/** 标签抽稀间隔：数据点多时每隔几个点显示一个标签 */
private fun labelEvery(size: Int): Int = when {
    size >= 25 -> 5
    size >= 15 -> 3
    size >= 10 -> 2
    else -> 1
}

/** 柱宽随数据点数量自适应 */
private fun barWidth(size: Int) = when {
    size >= 25 -> 3.dp
    size >= 15 -> 4.dp
    size >= 10 -> 6.dp
    else -> 9.dp
}

/**
 * 收支柱状对比（纯 Compose 布局实现，无第三方图表库）。
 * 支持 7~31 个数据点（周/月/年维度），柱宽与标签自动适配。
 */
@Composable
fun BarChart(
    points: List<ChartPoint>,
    expenseColor: Color,
    incomeColor: Color,
    modifier: Modifier = Modifier
) {
    val maxValue = points.maxOfOrNull { max(it.expense, it.income) }?.coerceAtLeast(1L) ?: 1L
    val barW = barWidth(points.size)
    val every = labelEvery(points.size)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        points.forEachIndexed { index, point ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val expenseH = (140.dp * (point.expense.toFloat() / maxValue))
                        .coerceAtLeast(if (point.expense > 0) 3.dp else 0.dp)
                    val incomeH = (140.dp * (point.income.toFloat() / maxValue))
                        .coerceAtLeast(if (point.income > 0) 3.dp else 0.dp)
                    Box(
                        modifier = Modifier
                            .width(barW)
                            .height(expenseH)
                            .background(expenseColor, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    )
                    Box(
                        modifier = Modifier
                            .width(barW)
                            .height(incomeH)
                            .background(incomeColor, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (index % every == 0) point.label else "",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 收支趋势折线（Canvas 自绘，无第三方图表库）。支持 7~31 个数据点。
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    expenseColor: Color,
    incomeColor: Color,
    modifier: Modifier = Modifier
) {
    val every = labelEvery(points.size)
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            if (points.size < 2) return@Canvas
            val maxValue = points.maxOfOrNull { max(it.expense, it.income) }?.coerceAtLeast(1L) ?: 1L
            val stepX = size.width / (points.size - 1)
            val expenseOffsets = points.mapIndexed { index, p ->
                Offset(
                    x = stepX * index,
                    y = size.height - (p.expense.toFloat() / maxValue) * (size.height - 16.dp.toPx())
                )
            }
            val incomeOffsets = points.mapIndexed { index, p ->
                Offset(
                    x = stepX * index,
                    y = size.height - (p.income.toFloat() / maxValue) * (size.height - 16.dp.toPx())
                )
            }
            for (i in 0 until expenseOffsets.size - 1) {
                drawLine(
                    color = expenseColor,
                    start = expenseOffsets[i],
                    end = expenseOffsets[i + 1],
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = incomeColor,
                    start = incomeOffsets[i],
                    end = incomeOffsets[i + 1],
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            points.forEachIndexed { index, p ->
                if (p.expense > 0) {
                    drawCircle(expenseColor, radius = 3.dp.toPx(), center = expenseOffsets[index])
                }
                if (p.income > 0) {
                    drawCircle(incomeColor, radius = 3.dp.toPx(), center = incomeOffsets[index])
                }
            }
        }
        // 底部标签（抽稀显示）
        Row(Modifier.fillMaxWidth()) {
            points.forEachIndexed { index, point ->
                Text(
                    text = if (index % every == 0) point.label else "",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
