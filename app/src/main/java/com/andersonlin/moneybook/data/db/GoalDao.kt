package com.andersonlin.moneybook.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.andersonlin.moneybook.data.model.Goal
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Query("SELECT * FROM goals ORDER BY deadlineEpochDay ASC, id ASC")
    fun getAllGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals ORDER BY deadlineEpochDay ASC, id ASC")
    suspend fun getAllSnapshot(): List<Goal>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getById(id: Long): Goal?

    @Insert
    suspend fun insert(goal: Goal): Long

    @Update
    suspend fun update(goal: Goal)

    @Delete
    suspend fun delete(goal: Goal)

    // ---- 备份 / 恢复 ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<Goal>)

    @Query("DELETE FROM goals")
    suspend fun deleteAll()
}
