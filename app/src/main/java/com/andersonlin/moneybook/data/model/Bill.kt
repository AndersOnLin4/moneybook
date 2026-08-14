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
        )
    ],
    indices = [Index("dateEpochDay"), Index("categoryId"), Index("type")]
)
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: Int,
    val amountCents: Long,
    val categoryId: Long,
    val note: String = "",
    val dateEpochDay: Long,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_EXPENSE = 0
        const val TYPE_INCOME = 1
    }
}
