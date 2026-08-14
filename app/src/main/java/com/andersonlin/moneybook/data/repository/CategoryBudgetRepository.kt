package com.andersonlin.moneybook.data.repository

import com.andersonlin.moneybook.data.db.CategoryBudgetDao
import com.andersonlin.moneybook.data.model.CategoryBudget

/** 分类独立预算仓库 */
class CategoryBudgetRepository(private val dao: CategoryBudgetDao) {

    fun getAllBudgets() = dao.getAllBudgets()

    suspend fun getForMonth(categoryId: Long, year: Int, month: Int): CategoryBudget? =
        dao.getForMonth(categoryId, year, month)

    /** 设置某分类某月预算；金额为 0 时删除 */
    suspend fun setBudget(categoryId: Long, year: Int, month: Int, amountCents: Long) {
        if (amountCents <= 0) {
            dao.getForMonth(categoryId, year, month)?.let { dao.delete(it) }
        } else {
            dao.upsert(CategoryBudget(categoryId = categoryId, year = year, month = month, amountCents = amountCents))
        }
    }

    suspend fun deleteBudget(budget: CategoryBudget) = dao.delete(budget)
}
