package com.andersonlin.moneybook.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 账单实体
 *
 * @param type         0 = 支出，1 = 收入
 * @param amountCents  金额，单位「分」（避免浮点误差）
 * @param accountId    所属账户（1 = 默认「现金」账户）
 * @param ledgerId     所属账本（1 = 默认账本）
 * @param dateEpochDay 记账日期（LocalDate.toEpochDay()），只精确到天
 */
@Entity(
    tableName = "bills",
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
    indices = [
        Index("dateEpochDay"), Index("categoryId"), Index("type"),
        Index("accountId"), Index("ledgerId")
    ]
)
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: Int,
    val amountCents: Long,
    val categoryId: Long,
    val accountId: Long = DEFAULT_ACCOUNT_ID,
    val ledgerId: Long = DEFAULT_LEDGER_ID,
    val note: String = "",
    val dateEpochDay: Long,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_EXPENSE = 0
        const val TYPE_INCOME = 1

        /** 默认账户（现金）的 id，由迁移/建库时的种子数据保证 */
        const val DEFAULT_ACCOUNT_ID = 1L

        /** 默认账本的 id */
        const val DEFAULT_LEDGER_ID = 1L
    }
}
