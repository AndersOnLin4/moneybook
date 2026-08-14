package com.andersonlin.moneybook.data.model

/** 内置默认账户：现金、微信、支付宝、银行卡（首次建库写入，id 1 = 现金） */
object DefaultAccounts {

    val ALL = listOf(
        Account(name = "现金", icon = "💵", isDefault = true, sortOrder = 1),
        Account(name = "微信", icon = "💚", isDefault = true, sortOrder = 2),
        Account(name = "支付宝", icon = "🔵", isDefault = true, sortOrder = 3),
        Account(name = "银行卡", icon = "💳", isDefault = true, sortOrder = 4)
    )
}
