package com.andersonlin.moneybook.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 分类独立预算：某个支出分类在某「年-月」的限额（金额单位为分）。
 */
@Entity(
    tableName = "category_budgets",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["categoryId", "year", "month"], unique = true)]
)
data class CategoryBudget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val categoryId: Long,
    val year: Int,
    val month: Int,
    val amountCents: Long
)
