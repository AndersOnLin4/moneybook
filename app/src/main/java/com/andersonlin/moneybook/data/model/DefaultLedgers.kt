package com.andersonlin.moneybook.data.model

/** 内置默认账本（首次建库/迁移时写入） */
object DefaultLedgers {
    val DEFAULT = Ledger(name = "默认账本", icon = "📒", sortOrder = 1)
}
