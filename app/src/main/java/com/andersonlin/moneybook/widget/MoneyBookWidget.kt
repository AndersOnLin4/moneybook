package com.andersonlin.moneybook.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.andersonlin.moneybook.MainActivity
import com.andersonlin.moneybook.MoneyBookApp
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.util.endEpochDay
import com.andersonlin.moneybook.util.formatCents
import com.andersonlin.moneybook.util.startEpochDay
import kotlinx.coroutines.flow.first
import java.time.YearMonth

/** 桌面小组件：本月结余 + 快捷记账入口 */
class MoneyBookWidget : GlanceAppWidget() {

    companion object {
        /** 刷新所有已放置的小组件实例 */
        @OptIn(ExperimentalGlanceApi::class)
        suspend fun updateAllWidgets(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, MoneyBookWidgetReceiver::class.java)
                val ids = manager.getAppWidgetIds(component)
                val widget = MoneyBookWidget()
                ids.forEach { id -> widget.update(context, AppWidgetId(id)) }
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as MoneyBookApp
        val month = YearMonth.now()
        val ledgerId = app.ledgerRepository.getActiveLedgerId()
        val sums = app.billRepository
            .getMonthSummary(ledgerId, month.startEpochDay(), month.endEpochDay())
            .first()
        val income = sums.firstOrNull { it.type == Bill.TYPE_INCOME }?.total ?: 0L
        val expense = sums.firstOrNull { it.type == Bill.TYPE_EXPENSE }?.total ?: 0L
        val budgetCents = app.budgetRepository.getForMonth(month.year, month.monthValue)?.amountCents
        val ledgerName = app.ledgerRepository.getById(ledgerId)?.name ?: "账本"
        provideContent {
            WidgetContent(
                month = month,
                income = income,
                expense = expense,
                budgetCents = budgetCents,
                ledgerName = ledgerName,
                context = context
            )
        }
    }
}

@OptIn(ExperimentalGlanceApi::class)
@Composable
private fun WidgetContent(
    month: YearMonth,
    income: Long,
    expense: Long,
    budgetCents: Long?,
    ledgerName: String,
    context: Context
) {
    GlanceTheme {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(16.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = "记一笔 · $ledgerName",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = GlanceTheme.colors.onSurface
                    )
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = "${month.year}年${month.monthValue}月",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = GlanceTheme.colors.onSurface
                    )
                )
                Spacer(GlanceModifier.width(10.dp))
                Button(
                    text = "↻",
                    onClick = actionRunCallback<WidgetRefreshCallback>(actionParametersOf()),
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.primary
                    ),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = ColorProvider(Color(0x14000000)),
                        contentColor = GlanceTheme.colors.primary
                    )
                )
            }
            Spacer(GlanceModifier.height(10.dp))
            Text(
                text = "本月结余",
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface)
            )
            Text(
                text = formatCents(income - expense),
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = GlanceTheme.colors.primary
                )
            )
            Spacer(GlanceModifier.height(10.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                Text(
                    text = "收入 +" + formatCents(income),
                    style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.primary),
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = "支出 -" + formatCents(expense),
                    style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.error),
                    modifier = GlanceModifier.defaultWeight()
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            Text(
                text = when {
                    budgetCents == null -> "本月未设置预算"
                    expense > budgetCents -> "预算已超支 " + formatCents(expense - budgetCents)
                    else -> "预算剩余 " + formatCents(budgetCents - expense)
                },
                style = TextStyle(
                    fontSize = 12.sp,
                    color = if (budgetCents != null && expense > budgetCents) {
                        GlanceTheme.colors.error
                    } else {
                        GlanceTheme.colors.onSurface
                    }
                )
            )
            Spacer(GlanceModifier.height(14.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                Button(
                    text = "＋ 记一笔",
                    onClick = actionStartActivity(addBillIntent(context), actionParametersOf()),
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorProvider(Color.White)
                    ),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = GlanceTheme.colors.primary,
                        contentColor = ColorProvider(Color.White)
                    )
                )
                Spacer(GlanceModifier.width(12.dp))
                Button(
                    text = "打开",
                    onClick = actionStartActivity(openAppIntent(context), actionParametersOf()),
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurface
                    ),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = ColorProvider(Color(0x1F000000)),
                        contentColor = GlanceTheme.colors.onSurface
                    )
                )
            }
        }
    }
}

private fun addBillIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_OPEN_ADD, true)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

private fun openAppIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
