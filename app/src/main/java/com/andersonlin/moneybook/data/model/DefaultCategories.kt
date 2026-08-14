package com.andersonlin.moneybook.data.model

/**
 * 内置默认分类：首次创建数据库时写入。
 * 支出：餐饮、交通、购物、住宿、娱乐、医疗
 * 收入：工资、红包、兼职
 */
object DefaultCategories {

    val EXPENSE = listOf(
        Category(name = "餐饮", type = Category.TYPE_EXPENSE, icon = "🍜", isDefault = true, sortOrder = 1),
        Category(name = "交通", type = Category.TYPE_EXPENSE, icon = "🚌", isDefault = true, sortOrder = 2),
        Category(name = "购物", type = Category.TYPE_EXPENSE, icon = "🛍️", isDefault = true, sortOrder = 3),
        Category(name = "住宿", type = Category.TYPE_EXPENSE, icon = "🏨", isDefault = true, sortOrder = 4),
        Category(name = "娱乐", type = Category.TYPE_EXPENSE, icon = "🎮", isDefault = true, sortOrder = 5),
        Category(name = "医疗", type = Category.TYPE_EXPENSE, icon = "💊", isDefault = true, sortOrder = 6)
    )

    val INCOME = listOf(
        Category(name = "工资", type = Category.TYPE_INCOME, icon = "💼", isDefault = true, sortOrder = 1),
        Category(name = "红包", type = Category.TYPE_INCOME, icon = "🧧", isDefault = true, sortOrder = 2),
        Category(name = "兼职", type = Category.TYPE_INCOME, icon = "💰", isDefault = true, sortOrder = 3)
    )

    val ALL: List<Category> = EXPENSE + INCOME
}
