package com.andersonlin.moneybook.data.repository

import androidx.room.withTransaction
import com.andersonlin.moneybook.data.db.AppDatabase
import com.andersonlin.moneybook.data.db.RecurringBillDao
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.RecurringBill
import java.time.LocalDate

/** 周期账单仓库：管理周期账单，并在 App 启动时生成到期账单 */
class RecurringRepository(
    private val database: AppDatabase,
    private val dao: RecurringBillDao
) {

    fun getAll() = dao.getAll()

    suspend fun add(recurringBill: RecurringBill): Long = dao.insert(recurringBill)

    suspend fun update(recurringBill: RecurringBill) = dao.update(recurringBill)

    suspend fun delete(recurringBill: RecurringBill) = dao.delete(recurringBill)

    suspend fun deleteByCategory(categoryId: Long) = dao.deleteByCategory(categoryId)

    /**
     * 生成所有到期的周期账单（App 启动时调用）。
     * 每条周期账单从 lastGenerated 的下一个周期开始，逐期补记到今天为止。
     */
    suspend fun generateDue() = runCatching {
        val today = LocalDate.now().toEpochDay()
        database.withTransaction {
            dao.getAllSnapshot().forEach { item ->
                var cursor = item
                var next = nextOccurrenceEpochDay(cursor)
                while (next != null && next <= today && cursor.enabled) {
                    database.billDao().insert(
                        Bill(
                            type = cursor.type,
                            amountCents = cursor.amountCents,
                            categoryId = cursor.categoryId,
                            accountId = cursor.accountId,
                            ledgerId = cursor.ledgerId,
                            note = cursor.note,
                            dateEpochDay = next
                        )
                    )
                    dao.updateLastGenerated(cursor.id, next)
                    cursor = cursor.copy(lastGeneratedEpochDay = next)
                    next = nextOccurrenceEpochDay(cursor)
                }
            }
        }
    }

    /** 计算下一个生成日；按月/年推进时处理月末与闰年截断（如 1月31日 + 1月 → 2月28/29日） */
    private fun nextOccurrenceEpochDay(item: RecurringBill): Long? {
        val last = LocalDate.ofEpochDay(item.lastGeneratedEpochDay)
        val next = when (item.cycle) {
            RecurringBill.CYCLE_WEEKLY -> last.plusWeeks(1)
            RecurringBill.CYCLE_MONTHLY -> addMonthsClamped(last, 1)
            RecurringBill.CYCLE_YEARLY -> addYearsClamped(last, 1)
            else -> return null
        }
        return next.toEpochDay()
    }

    /** 加月并处理月末截断：1月31日 + 1月 → 2月28/29日 */
    private fun addMonthsClamped(date: LocalDate, months: Long): LocalDate {
        val target = date.plusMonths(months)
        return if (target.dayOfMonth < date.dayOfMonth) {
            target.withDayOfMonth(target.lengthOfMonth())
        } else {
            target
        }
    }

    /** 加年并处理 2月29日 → 平年 2月28日 */
    private fun addYearsClamped(date: LocalDate, years: Long): LocalDate {
        val target = date.plusYears(years)
        return if (target.dayOfMonth < date.dayOfMonth) {
            target.withDayOfMonth(target.lengthOfMonth())
        } else {
            target
        }
    }
}
