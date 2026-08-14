package com.andersonlin.moneybook.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 月度预算：每个「年-月」一条记录，金额单位为分。
 * 预算针对当月总支出（收入不计入）。
 */
@Entity(
    tableName = "budgets",
    indices = [Index(value = ["year", "month"], unique = true)]
)
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val year: Int,
    val month: Int,
    val amountCents: Long
)
