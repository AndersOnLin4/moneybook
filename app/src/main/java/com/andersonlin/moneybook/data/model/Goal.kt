package com.andersonlin.moneybook.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 存钱目标：目标金额 + 截止日期 + 已存金额（手动「存入」累计）。
 * 每月需存 = (目标 - 已存) / 剩余月数（向上取整）。
 */
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val icon: String,
    val targetCents: Long,
    val savedCents: Long = 0L,
    val deadlineEpochDay: Long,
    val createdAt: Long = System.currentTimeMillis()
)
