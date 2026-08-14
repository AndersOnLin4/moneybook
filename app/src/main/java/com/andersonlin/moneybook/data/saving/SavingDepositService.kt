package com.andersonlin.moneybook.data.saving

import androidx.room.withTransaction
import com.andersonlin.moneybook.data.db.AppDatabase
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.data.model.Goal
import java.time.LocalDate

/**
 * 存钱服务：存入一笔时——
 * 1. 累计目标已存进度；
 * 2. 自动生成一笔「储蓄」支出账单（进入统计图与账单列表，削减当月结余）。
 * 「储蓄」分类按需自动创建（内置保护，不可删除）。
 */
class SavingDepositService(private val database: AppDatabase) {

    suspend fun deposit(goal: Goal, cents: Long, ledgerId: Long) {
        database.withTransaction {
            database.goalDao().update(goal.copy(savedCents = goal.savedCents + cents))
            val savingCategory = findOrCreateSavingCategory()
            database.billDao().insert(
                Bill(
                    type = Bill.TYPE_EXPENSE,
                    amountCents = cents,
                    categoryId = savingCategory.id,
                    accountId = Bill.DEFAULT_ACCOUNT_ID,
                    ledgerId = ledgerId,
                    note = "存入：${goal.name}",
                    dateEpochDay = LocalDate.now().toEpochDay()
                )
            )
        }
    }

    /** 查找「储蓄」分类，不存在则创建（isDefault 保护不可删除） */
    private suspend fun findOrCreateSavingCategory(): Category {
        val expenseCategories = database.categoryDao()
            .getCategoriesByTypeSnapshot(Category.TYPE_EXPENSE)
        expenseCategories.firstOrNull { it.name == "储蓄" }?.let { return it }
        val maxOrder = expenseCategories.maxOfOrNull { it.sortOrder } ?: 0
        val id = database.categoryDao().insert(
            Category(
                name = "储蓄",
                type = Category.TYPE_EXPENSE,
                icon = "💰",
                isDefault = true,
                sortOrder = maxOrder + 1
            )
        )
        return Category(
            id = id,
            name = "储蓄",
            type = Category.TYPE_EXPENSE,
            icon = "💰",
            isDefault = true,
            sortOrder = maxOrder + 1
        )
    }
}
