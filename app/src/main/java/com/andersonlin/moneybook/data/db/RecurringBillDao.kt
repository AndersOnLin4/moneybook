package com.andersonlin.moneybook.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.andersonlin.moneybook.data.model.RecurringBill
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringBillDao {

    @Query("SELECT * FROM recurring_bills ORDER BY id DESC")
    fun getAll(): Flow<List<RecurringBill>>

    @Query("SELECT * FROM recurring_bills")
    suspend fun getAllSnapshot(): List<RecurringBill>

    @Insert
    suspend fun insert(recurringBill: RecurringBill): Long

    @Update
    suspend fun update(recurringBill: RecurringBill)

    @Delete
    suspend fun delete(recurringBill: RecurringBill)

    /** 推进生成游标，防止重复生成 */
    @Query("UPDATE recurring_bills SET lastGeneratedEpochDay = :day WHERE id = :id")
    suspend fun updateLastGenerated(id: Long, day: Long)

    @Query("DELETE FROM recurring_bills WHERE categoryId = :categoryId")
    suspend fun deleteByCategory(categoryId: Long)

    @Query("DELETE FROM recurring_bills WHERE ledgerId = :ledgerId")
    suspend fun deleteByLedger(ledgerId: Long)

    @Query("UPDATE recurring_bills SET accountId = :newId WHERE accountId = :oldId")
    suspend fun reassignAccount(oldId: Long, newId: Long)

    // ---- 备份 / 恢复 ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RecurringBill>)

    @Query("DELETE FROM recurring_bills")
    suspend fun deleteAll()
}
