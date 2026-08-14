package com.andersonlin.moneybook.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.andersonlin.moneybook.data.model.Ledger
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    @Query("SELECT * FROM ledgers ORDER BY sortOrder ASC, id ASC")
    fun getAllLedgers(): Flow<List<Ledger>>

    @Query("SELECT * FROM ledgers ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllSnapshot(): List<Ledger>

    @Query("SELECT * FROM ledgers WHERE id = :id")
    suspend fun getById(id: Long): Ledger?

    @Query("SELECT COUNT(*) FROM ledgers")
    suspend fun count(): Int

    @Insert
    suspend fun insert(ledger: Ledger): Long

    @Update
    suspend fun update(ledger: Ledger)

    @Delete
    suspend fun delete(ledger: Ledger)

    // ---- 备份 / 恢复 ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(ledgers: List<Ledger>)

    @Query("DELETE FROM ledgers")
    suspend fun deleteAll()
}
