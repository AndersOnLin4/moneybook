package com.andersonlin.moneybook.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 金额（分）格式化为 "1,234.56"，负数显示 "-1,234.56" */
fun formatCents(cents: Long): String = amountFormat.format(cents / 100.0)

/** 金额（分）格式化为 "1234.56"（不带分组），用于编辑时回显 */
fun formatCentsPlain(cents: Long): String =
    String.format(Locale.US, "%.2f", cents / 100.0)

/** 文本金额转为「分」，非法或非正数返回 null */
fun String.toCents(): Long? = runCatching {
    val bd = BigDecimal(this.trim())
    if (bd.signum() <= 0) return null
    bd.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
}.getOrNull()

/** 月份第一天 / 最后一天的 epochDay（闭区间查询用） */
fun YearMonth.startEpochDay(): Long = atDay(1).toEpochDay()
fun YearMonth.endEpochDay(): Long = atEndOfMonth().toEpochDay()

fun Long.toYearMonth(): YearMonth = YearMonth.from(LocalDate.ofEpochDay(this))

/** "2024年5月" */
fun monthLabel(ym: YearMonth): String = "${ym.year}年${ym.monthValue}月"

/** "5月12日" */
fun dateLabel(epochDay: Long): String {
    val d = LocalDate.ofEpochDay(epochDay)
    return "${d.monthValue}月${d.dayOfMonth}日"
}

/** "2024年5月12日 周日" */
fun fullDateLabel(epochDay: Long): String {
    val d = LocalDate.ofEpochDay(epochDay)
    val week = d.format(DateTimeFormatter.ofPattern("EEE", Locale.CHINESE))
    return "${d.year}年${d.monthValue}月${d.dayOfMonth}日 $week"
}

private val amountFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.CHINA).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}
