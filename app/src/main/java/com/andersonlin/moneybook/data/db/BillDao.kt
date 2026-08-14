package com.andersonlin.moneybook.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.andersonlin.moneybook.data.model.Bill
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {

    @Insert
    suspend fun insert(bill: Bill): Long

    @Update
    suspend fun update(bill: Bill)

    @Delete
    suspend fun delete(bill: Bill)

    /** 全部账单，时间倒序 */
    @Query("SELECT * FROM bills ORDER BY dateEpochDay DESC, id DESC")
    fun getAllBills(): Flow<List<Bill>>

    /** 最近 N 条账单（首页预览） */
    @Query("SELECT * FROM bills ORDER BY dateEpochDay DESC, id DESC LIMIT :limit")
    fun getRecentBills(limit: Int): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getById(id: Long): Bill?

    /** 月度汇总：按类型分组求和 */
    data class MonthSum(val type: Int, val total: Long)

    @Query(
        "SELECT type, SUM(amountCents) AS total FROM bills " +
            "WHERE dateEpochDay BETWEEN :startDay AND :endDay GROUP BY type"
    )
    fun getMonthSummary(startDay: Long, endDay: Long): Flow<List<MonthSum>>

    /** 某类型在某时间段内各分类的金额合计（统计饼图用）；type 传 -1 表示收支全部 */
    data class CategorySum(val categoryId: Long, val total: Long)

    @Query(
        "SELECT categoryId, SUM(amountCents) AS total FROM bills " +
            "WHERE (:type = -1 OR type = :type) AND dateEpochDay BETWEEN :startDay AND :endDay " +
            "GROUP BY categoryId"
    )
    fun getCategoryStats(type: Int, startDay: Long, endDay: Long): Flow<List<CategorySum>>

    /** 逐月收支合计（柱状/趋势图用），ym 格式 "yyyy-MM" */
    data class MonthlyTotal(val ym: String, val type: Int, val total: Long)

    @Query(
        "SELECT strftime('%Y-%m', dateEpochDay * 86400, 'unixepoch') AS ym, type, " +
            "SUM(amountCents) AS total FROM bills " +
            "WHERE dateEpochDay BETWEEN :startDay AND :endDay " +
            "GROUP BY ym, type ORDER BY ym"
    )
    fun getMonthlyTotals(startDay: Long, endDay: Long): Flow<List<MonthlyTotal>>

    /** 逐日收支合计（柱状/趋势图用），day 格式 "yyyy-MM-dd" */
    data class DailyTotal(val day: String, val type: Int, val total: Long)

    @Query(
        "SELECT strftime('%Y-%m-%d', dateEpochDay * 86400, 'unixepoch') AS day, type, " +
            "SUM(amountCents) AS total FROM bills " +
            "WHERE dateEpochDay BETWEEN :startDay AND :endDay " +
            "GROUP BY day, type ORDER BY day"
    )
    fun getDailyTotals(startDay: Long, endDay: Long): Flow<List<DailyTotal>>

    /** 某天的收支合计 */
    @Query(
        "SELECT type, SUM(amountCents) AS total FROM bills " +
            "WHERE dateEpochDay = :day GROUP BY type"
    )
    fun getDaySummary(day: Long): Flow<List<MonthSum>>

    /**
     * 搜索 / 筛选：按类型过滤 + 关键字（备注或分类名模糊匹配）+ 金额区间，时间倒序。
     * typeFilter 传 -1 表示全部类型；minCents / maxCents 传 null 表示不限。
     */
    @Query(
        """
        SELECT b.* FROM bills b LEFT JOIN categories c ON b.categoryId = c.id
        WHERE (:typeFilter = -1 OR b.type = :typeFilter)
          AND (b.note LIKE '%' || :keyword || '%' OR c.name LIKE '%' || :keyword || '%')
          AND (:minCents IS NULL OR b.amountCents >= :minCents)
          AND (:maxCents IS NULL OR b.amountCents <= :maxCents)
        ORDER BY b.dateEpochDay DESC, b.id DESC
        """
    )
    fun searchBills(
        typeFilter: Int,
        keyword: String,
        minCents: Long?,
        maxCents: Long?
    ): Flow<List<Bill>>

    @Query("SELECT COUNT(*) FROM bills WHERE categoryId = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int

    @Query("DELETE FROM bills WHERE categoryId = :categoryId")
    suspend fun deleteByCategory(categoryId: Long)

    /** 账户删除时把账单转移到其它账户 */
    @Query("UPDATE bills SET accountId = :newId WHERE accountId = :oldId")
    suspend fun reassignAccount(oldId: Long, newId: Long)

    // ---- 备份 / 恢复 ----

    @Query("SELECT * FROM bills ORDER BY dateEpochDay ASC, id ASC")
    suspend fun getAllSnapshot(): List<Bill>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bills: List<Bill>)

    @Query("DELETE FROM bills")
    suspend fun deleteAll()
}
