package com.andersonlin.moneybook.data.repository

import com.andersonlin.moneybook.data.db.BillDao
import com.andersonlin.moneybook.data.model.Bill

/** 账单仓库：对 DAO 的薄封装，ViewModel 只依赖 Repository */
class BillRepository(private val dao: BillDao) {

    fun getAllBills() = dao.getAllBills()

    fun getRecentBills(limit: Int) = dao.getRecentBills(limit)

    suspend fun getById(id: Long): Bill? = dao.getById(id)

    fun getMonthSummary(startDay: Long, endDay: Long) = dao.getMonthSummary(startDay, endDay)

    fun getCategoryStats(type: Int, startDay: Long, endDay: Long) =
        dao.getCategoryStats(type, startDay, endDay)

    fun getMonthlyTotals(startDay: Long, endDay: Long) =
        dao.getMonthlyTotals(startDay, endDay)

    fun getDaySummary(day: Long) = dao.getDaySummary(day)

    fun searchBills(typeFilter: Int, keyword: String, minCents: Long? = null, maxCents: Long? = null) =
        dao.searchBills(typeFilter, keyword, minCents, maxCents)

    suspend fun insert(bill: Bill): Long = dao.insert(bill)

    suspend fun update(bill: Bill) = dao.update(bill)

    suspend fun delete(bill: Bill) = dao.delete(bill)

    suspend fun countByCategory(categoryId: Long): Int = dao.countByCategory(categoryId)

    suspend fun deleteByCategory(categoryId: Long) = dao.deleteByCategory(categoryId)

    suspend fun getAllSnapshot() = dao.getAllSnapshot()

    suspend fun insertAll(bills: List<Bill>) = dao.insertAll(bills)

    suspend fun deleteAll() = dao.deleteAll()
}
