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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * 近 6 月收支柱状对比（纯 Compose 布局实现，无第三方图表库）。
 * expenseColor / incomeColor 分别画两根柱子。
 */
@Composable
fun BarChart(
    groups: List<BarGroup>,
    expenseColor: Color,
    incomeColor: Color,
    modifier: Modifier = Modifier
) {
    val maxValue = groups.maxOfOrNull { max(it.expense, it.income) }?.coerceAtLeast(1L) ?: 1L
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        groups.forEach { group ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val expenseH = (140.dp * (group.expense.toFloat() / maxValue))
                        .coerceAtLeast(if (group.expense > 0) 3.dp else 0.dp)
                    val incomeH = (140.dp * (group.income.toFloat() / maxValue))
                        .coerceAtLeast(if (group.income > 0) 3.dp else 0.dp)
                    Box(
                        modifier = Modifier
                            .width(11.dp)
                            .height(expenseH)
                            .background(expenseColor, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    )
                    Box(
                        modifier = Modifier
                            .width(11.dp)
                            .height(incomeH)
                            .background(incomeColor, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = group.label,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 近 12 月收支趋势折线（Canvas 自绘，无第三方图表库）。
 */
@Composable
fun LineChart(
    points: List<LinePoint>,
    expenseColor: Color,
    incomeColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
            // 支出折线
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
            // 数据点
            points.forEachIndexed { index, p ->
                if (p.expense > 0) {
                    drawCircle(expenseColor, radius = 4.dp.toPx(), center = expenseOffsets[index])
                }
                if (p.income > 0) {
                    drawCircle(incomeColor, radius = 4.dp.toPx(), center = incomeOffsets[index])
                }
            }
        }
        // 底部月份标签（每 2 个月标一个）
        Row(Modifier.fillMaxWidth()) {
            points.forEachIndexed { index, point ->
                Text(
                    text = if (index % 2 == 0) point.label else "",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
