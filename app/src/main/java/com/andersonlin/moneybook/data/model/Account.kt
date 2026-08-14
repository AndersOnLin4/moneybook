package com.andersonlin.moneybook.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 账户实体（现金、微信、支付宝、银行卡等）
 *
 * @param icon      emoji 图标
 * @param isDefault 是否内置默认账户（内置账户也可删除，账单会转移到其它账户）
 * @param sortOrder 显示顺序
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val icon: String,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0
)
