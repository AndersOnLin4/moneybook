package com.andersonlin.moneybook.data.repository

import com.andersonlin.moneybook.data.db.BudgetDao
import com.andersonlin.moneybook.data.model.Budget

/** 月度预算仓库 */
class BudgetRepository(private val dao: BudgetDao) {

    fun getAllBudgets() = dao.getAllBudgets()

    suspend fun getForMonth(year: Int, month: Int): Budget? = dao.getForMonth(year, month)

    /** 设置或更新某月预算；金额为 0 时删除该月预算 */
    suspend fun setBudget(year: Int, month: Int, amountCents: Long) {
        if (amountCents <= 0) {
            dao.getForMonth(year, month)?.let { dao.delete(it) }
        } else {
            dao.upsert(Budget(year = year, month = month, amountCents = amountCents))
        }
    }

    suspend fun deleteBudget(budget: Budget) = dao.delete(budget)
}
