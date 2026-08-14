package com.andersonlin.moneybook.data.repository

import com.andersonlin.moneybook.data.db.BillDao
import com.andersonlin.moneybook.data.model.Bill

/** 账单仓库：对 DAO 的薄封装，ViewModel 只依赖 Repository。查询均按账本隔离。 */
class BillRepository(private val dao: BillDao) {

    fun getAllBills(ledgerId: Long) = dao.getAllBills(ledgerId)

    fun getRecentBills(ledgerId: Long, limit: Int) = dao.getRecentBills(ledgerId, limit)

    suspend fun getById(id: Long): Bill? = dao.getById(id)

    fun getMonthSummary(ledgerId: Long, startDay: Long, endDay: Long) =
        dao.getMonthSummary(ledgerId, startDay, endDay)

    fun getCategoryStats(ledgerId: Long, type: Int, startDay: Long, endDay: Long) =
        dao.getCategoryStats(ledgerId, type, startDay, endDay)

    fun getMonthlyTotals(ledgerId: Long, startDay: Long, endDay: Long) =
        dao.getMonthlyTotals(ledgerId, startDay, endDay)

    fun getDailyTotals(ledgerId: Long, startDay: Long, endDay: Long) =
        dao.getDailyTotals(ledgerId, startDay, endDay)

    fun getDaySummary(ledgerId: Long, day: Long) = dao.getDaySummary(ledgerId, day)

    fun searchBills(
        ledgerId: Long,
        typeFilter: Int,
        keyword: String,
        minCents: Long? = null,
        maxCents: Long? = null
    ) = dao.searchBills(ledgerId, typeFilter, keyword, minCents, maxCents)

    suspend fun insert(bill: Bill): Long = dao.insert(bill)

    suspend fun update(bill: Bill) = dao.update(bill)

    suspend fun delete(bill: Bill) = dao.delete(bill)

    suspend fun countByCategory(categoryId: Long): Int = dao.countByCategory(categoryId)

    suspend fun deleteByCategory(categoryId: Long) = dao.deleteByCategory(categoryId)

    suspend fun countByLedger(ledgerId: Long): Int = dao.countByLedger(ledgerId)

    suspend fun deleteByLedger(ledgerId: Long) = dao.deleteByLedger(ledgerId)

    suspend fun getAllSnapshot() = dao.getAllSnapshot()

    suspend fun getSnapshotForLedger(ledgerId: Long) = dao.getSnapshotForLedger(ledgerId)

    suspend fun insertAll(bills: List<Bill>) = dao.insertAll(bills)

    suspend fun deleteAll() = dao.deleteAll()
}
