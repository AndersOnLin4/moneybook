package com.andersonlin.moneybook.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 账本：每个账本拥有独立的账单集合。
 * id = 1 为内置「默认账本」，不可删除。
 */
@Entity(tableName = "ledgers")
data class Ledger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val icon: String,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_ID = 1L
    }
}
