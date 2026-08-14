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

    /** 某类型在某时间段内各分类的金额合计（统计饼图用） */
    data class CategorySum(val categoryId: Long, val total: Long)

    @Query(
        "SELECT categoryId, SUM(amountCents) AS total FROM bills " +
            "WHERE type = :type AND dateEpochDay BETWEEN :startDay AND :endDay " +
            "GROUP BY categoryId"
    )
    fun getCategoryStats(type: Int, startDay: Long, endDay: Long): Flow<List<CategorySum>>

    /**
     * 搜索 / 筛选：按类型过滤 + 关键字（备注或分类名模糊匹配），时间倒序。
     * typeFilter 传 -1 表示全部类型。
     */
    @Query(
        """
        SELECT b.* FROM bills b LEFT JOIN categories c ON b.categoryId = c.id
        WHERE (:typeFilter = -1 OR b.type = :typeFilter)
          AND (b.note LIKE '%' || :keyword || '%' OR c.name LIKE '%' || :keyword || '%')
        ORDER BY b.dateEpochDay DESC, b.id DESC
        """
    )
    fun searchBills(typeFilter: Int, keyword: String): Flow<List<Bill>>

    @Query("SELECT COUNT(*) FROM bills WHERE categoryId = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int

    @Query("DELETE FROM bills WHERE categoryId = :categoryId")
    suspend fun deleteByCategory(categoryId: Long)

    // ---- 备份 / 恢复 ----

    @Query("SELECT * FROM bills ORDER BY dateEpochDay ASC, id ASC")
    suspend fun getAllSnapshot(): List<Bill>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bills: List<Bill>)

    @Query("DELETE FROM bills")
    suspend fun deleteAll()
}
