package com.andersonlin.moneybook.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 环形占比图（不依赖第三方图表库，Canvas 自绘）。
 * slices 按金额从大到小排序，颜色循环取自 colors。
 */
@Composable
fun DonutChart(
    slices: List<StatSlice>,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    val total = slices.sumOf { it.total }.coerceAtLeast(1L)
    Canvas(modifier = modifier) {
        val strokeWidth = 36.dp.toPx()
        val inset = strokeWidth / 2
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(inset, inset)
        var startAngle = -90f
        slices.forEachIndexed { index, slice ->
            val sweep = 360f * slice.total / total
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            startAngle += sweep
        }
    }
}
