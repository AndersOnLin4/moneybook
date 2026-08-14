package com.andersonlin.moneybook.ui.stats

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.sqrt

/**
 * 统计图导出渲染器：用原生 Canvas 把当前统计状态重绘为 Bitmap（PNG）。
 * 固定使用浅色配色，包含标题与图例，适合分享与存档。
 */
object ChartBitmapRenderer {

    private const val BG = 0xFFFFFFFF.toInt()
    private const val TEXT = 0xFF171D16.toInt()
    private const val SUBTEXT = 0xFF727970.toInt()
    private const val EXPENSE = 0xFFBA1A1A.toInt()
    private const val INCOME = 0xFF2E7D32.toInt()
    private const val TERTIARY = 0xFF38656A.toInt()

    private val PIE_COLORS = intArrayOf(
        0xFF4CAF50.toInt(), 0xFFFF9800.toInt(), 0xFF2196F3.toInt(), 0xFFE91E63.toInt(),
        0xFF9C27B0.toInt(), 0xFF00BCD4.toInt(), 0xFFF44336.toInt(), 0xFF795548.toInt(),
        0xFF3F51B5.toInt(), 0xFFFFC107.toInt(), 0xFF009688.toInt(), 0xFF8BC34A.toInt()
    )

    fun render(state: StatsUiState, width: Int = 1080, height: Int = 1280): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BG)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT
            textSize = 48f
            isFakeBoldText = true
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SUBTEXT
            textSize = 34f
        }
        // 标题
        val title = when (state.chartType) {
            StatsViewModel.CHART_PIE -> "${state.label} · 分类占比"
            StatsViewModel.CHART_CALENDAR -> "${state.calendarMonth.year}年${state.calendarMonth.monthValue}月 · 日历"
            else -> state.chartWindowLabel
        }
        canvas.drawText(title, 48f, 84f, titlePaint)
        val subTitle = when (state.chartType) {
            StatsViewModel.CHART_PIE ->
                (if (state.type == StatsViewModel.TYPE_ALL) "总流水" else "总${state.typeLabel}") +
                    "  " + com.andersonlin.moneybook.util.formatCents(state.total)
            else -> "支出(红)  收入(绿)"
        }
        canvas.drawText(subTitle, 48f, 140f, subPaint)

        when (state.chartType) {
            StatsViewModel.CHART_PIE -> drawPie(canvas, state, width, height)
            StatsViewModel.CHART_CALENDAR -> drawCalendar(canvas, state, width, height)
            StatsViewModel.CHART_BAR -> drawBars(canvas, state, width, height)
            else -> drawLines(canvas, state, width, height)
        }
        return bitmap
    }

    private fun drawPie(canvas: Canvas, state: StatsUiState, w: Int, h: Int) {
        val total = state.total.coerceAtLeast(1L)
        val cx = w / 2f
        val cy = 420f
        val radius = 240f
        val stroke = 130f
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        var start = -90f
        state.slices.forEachIndexed { index, slice ->
            val sweep = 360f * slice.total / total
            paint.color = PIE_COLORS[index % PIE_COLORS.size]
            canvas.drawArc(rect, start, sweep, false, paint)
            start += sweep
        }
        // 中心文字
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT
            textSize = 44f
            isFakeBoldText = true
        }
        val center = com.andersonlin.moneybook.util.formatCents(state.total)
        val tw = centerPaint.measureText(center)
        canvas.drawText(center, cx - tw / 2, cy + 14f, centerPaint)
        // 图例
        var ly = 800f
        val legendText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT; textSize = 36f }
        val legendPct = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SUBTEXT; textSize = 32f }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        state.slices.take(12).forEachIndexed { index, slice ->
            dot.color = PIE_COLORS[index % PIE_COLORS.size]
            canvas.drawCircle(72f, ly - 10f, 16f, dot)
            canvas.drawText("${slice.category.icon} ${slice.category.name}", 112f, ly, legendText)
            val amount = (if (slice.category.type == com.andersonlin.moneybook.data.model.Bill.TYPE_INCOME) "+" else "-") +
                com.andersonlin.moneybook.util.formatCents(slice.total)
            canvas.drawText(amount, 560f, ly, legendText)
            canvas.drawText("%.1f%%".format(slice.total * 100.0 / total), 880f, ly, legendPct)
            ly += 68f
        }
    }

    private fun drawBars(canvas: Canvas, state: StatsUiState, w: Int, h: Int) {
        val points = state.chartPoints
        if (points.isEmpty()) return
        val maxValue = points.maxOfOrNull { maxOf(it.expense, it.income) }?.coerceAtLeast(1L) ?: 1L
        val area = RectF(60f, 220f, w - 60f, 1060f)
        val barW = (area.width() / points.size) * 0.32f
        val gap = area.width() / points.size
        val expPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = EXPENSE }
        val incPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INCOME }
        points.forEachIndexed { index, p ->
            val xBase = area.left + index * gap + gap * 0.25f
            val eh = (p.expense.toFloat() / maxValue) * (area.height() - 160f)
            val ih = (p.income.toFloat() / maxValue) * (area.height() - 160f)
            canvas.drawRoundRect(
                RectF(xBase, area.bottom - 160f - eh, xBase + barW, area.bottom - 160f),
                8f, 8f, expPaint
            )
            canvas.drawRoundRect(
                RectF(xBase + barW + 6f, area.bottom - 160f - ih, xBase + barW * 2 + 6f, area.bottom - 160f),
                8f, 8f, incPaint
            )
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SUBTEXT; textSize = 28f }
        val every = if (points.size >= 25) 5 else if (points.size >= 15) 3 else 1
        points.forEachIndexed { index, p ->
            if (index % every == 0) {
                canvas.drawText(p.label, area.left + index * gap, area.bottom - 110f, labelPaint)
            }
        }
    }

    private fun drawLines(canvas: Canvas, state: StatsUiState, w: Int, h: Int) {
        val points = state.chartPoints
        if (points.size < 2) return
        val maxValue = points.maxOfOrNull { maxOf(it.expense, it.income) }?.coerceAtLeast(1L) ?: 1L
        val area = RectF(80f, 220f, w - 80f, 1000f)
        val stepX = area.width() / (points.size - 1)
        val expPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXPENSE; strokeWidth = 8f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        val incPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INCOME; strokeWidth = 8f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        fun offsets(selector: (ChartPoint) -> Long): List<Pair<Float, Float>> = points.mapIndexed { index, p ->
            area.left + index * stepX to
                (area.bottom - (selector(p).toFloat() / maxValue) * (area.height() - 80f))
        }
        val eOffsets = offsets { it.expense }
        val iOffsets = offsets { it.income }
        for (i in 0 until eOffsets.size - 1) {
            canvas.drawLine(eOffsets[i].first, eOffsets[i].second, eOffsets[i + 1].first, eOffsets[i + 1].second, expPaint)
            canvas.drawLine(iOffsets[i].first, iOffsets[i].second, iOffsets[i + 1].first, iOffsets[i + 1].second, incPaint)
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        points.forEachIndexed { index, p ->
            if (p.expense > 0) { dot.color = EXPENSE; canvas.drawCircle(eOffsets[index].first, eOffsets[index].second, 10f, dot) }
            if (p.income > 0) { dot.color = INCOME; canvas.drawCircle(iOffsets[index].first, iOffsets[index].second, 10f, dot) }
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SUBTEXT; textSize = 28f }
        val every = if (points.size >= 25) 5 else if (points.size >= 15) 3 else 2
        points.forEachIndexed { index, p ->
            if (index % every == 0) {
                canvas.drawText(p.label, area.left + index * stepX, area.bottom + 60f, labelPaint)
            }
        }
    }

    private fun drawCalendar(canvas: Canvas, state: StatsUiState, w: Int, h: Int) {
        val days = state.calendarDays
        if (days.isEmpty()) return
        val firstOffset = days.first().date.dayOfWeek.value - 1
        val cells = List(firstOffset) { null } + days
        val gridLeft = 60f
        val gridRight = w - 60f
        val gridTop = 240f
        val cellW = (gridRight - gridLeft) / 7f
        val cellH = 110f
        val circleColor = when (state.type) {
            StatsViewModel.TYPE_INCOME -> INCOME
            StatsViewModel.TYPE_ALL -> TERTIARY
            else -> EXPENSE
        }
        val dayValue: (CalendarDay) -> Long = { d ->
            when (state.type) {
                StatsViewModel.TYPE_INCOME -> d.income
                StatsViewModel.TYPE_ALL -> d.expense + d.income
                else -> d.expense
            }
        }
        val maxValue = days.maxOfOrNull(dayValue)?.coerceAtLeast(1L) ?: 1L
        val weekLabels = arrayOf("一", "二", "三", "四", "五", "六", "日")
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SUBTEXT; textSize = 30f }
        repeat(7) { i ->
            canvas.drawText(weekLabels[i], gridLeft + i * cellW + cellW / 2 - 14f, gridTop - 20f, labelPaint)
        }
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = circleColor; alpha = 90
        }
        val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT; textSize = 32f; isFakeBoldText = true }
        cells.chunked(7).forEachIndexed { row, week ->
            week.forEachIndexed { col, day ->
                val cx = gridLeft + col * cellW + cellW / 2
                val cy = gridTop + row * cellH + cellH / 2
                if (day != null) {
                    val value = dayValue(day)
                    if (value > 0) {
                        val radius = 12f + 26f * sqrt(value.toFloat() / maxValue)
                        canvas.drawCircle(cx, cy, radius, circlePaint)
                    }
                    canvas.drawText(day.date.dayOfMonth.toString(), cx - 16f, cy + 12f, dayPaint)
                }
            }
        }
    }
}
