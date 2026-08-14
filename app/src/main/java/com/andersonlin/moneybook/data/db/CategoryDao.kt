package com.andersonlin.moneybook.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.andersonlin.moneybook.data.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    /** 全部分类：支出在前、收入在后，按 sortOrder 排序 */
    @Query("SELECT * FROM categories ORDER BY type ASC, sortOrder ASC, id ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sortOrder ASC, id ASC")
    fun getCategoriesByType(type: Int): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): Category?

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sortOrder ASC, id ASC")
    suspend fun getCategoriesByTypeSnapshot(type: Int): List<Category>

    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    // ---- 备份 / 恢复 ----

    @Query("SELECT * FROM categories ORDER BY type ASC, sortOrder ASC, id ASC")
    suspend fun getAllSnapshot(): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<Category>)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
