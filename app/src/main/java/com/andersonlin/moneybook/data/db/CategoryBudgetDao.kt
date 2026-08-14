package com.andersonlin.moneybook.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andersonlin.moneybook.data.model.CategoryBudget
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryBudgetDao {

    @Query("SELECT * FROM category_budgets ORDER BY year DESC, month DESC, categoryId ASC")
    fun getAllBudgets(): Flow<List<CategoryBudget>>

    @Query(
        "SELECT * FROM category_budgets WHERE categoryId = :categoryId " +
            "AND year = :year AND month = :month LIMIT 1"
    )
    suspend fun getForMonth(categoryId: Long, year: Int, month: Int): CategoryBudget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: CategoryBudget)

    @Delete
    suspend fun delete(budget: CategoryBudget)

    @Query("DELETE FROM category_budgets WHERE categoryId = :categoryId")
    suspend fun deleteByCategory(categoryId: Long)
}
