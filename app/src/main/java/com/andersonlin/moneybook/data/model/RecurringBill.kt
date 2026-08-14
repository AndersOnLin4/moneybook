package com.andersonlin.moneybook.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 周期账单：按周/月/年自动生成账单。
 *
 * @param cycle                  0 = 每月，1 = 每周，2 = 每年
 * @param ledgerId               所属账本
 * @param startEpochDay          开始日期（首个周期从此日期的下一个周期开始生成）
 * @param lastGeneratedEpochDay  最近一次已生成账单的日期（推进游标，防重复）
 */
@Entity(
    tableName = "recurring_bills",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Ledger::class,
            parentColumns = ["id"],
            childColumns = ["ledgerId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("categoryId"), Index("accountId"), Index("ledgerId")]
)
data class RecurringBill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: Int,
    val amountCents: Long,
    val categoryId: Long,
    val accountId: Long = Bill.DEFAULT_ACCOUNT_ID,
    val ledgerId: Long = Bill.DEFAULT_LEDGER_ID,
    val note: String = "",
    val cycle: Int,
    val startEpochDay: Long,
    val lastGeneratedEpochDay: Long,
    val enabled: Boolean = true
) {
    companion object {
        const val CYCLE_MONTHLY = 0
        const val CYCLE_WEEKLY = 1
        const val CYCLE_YEARLY = 2
    }
}
