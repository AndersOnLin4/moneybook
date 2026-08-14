package com.andersonlin.moneybook.data.repository

import com.andersonlin.moneybook.data.db.CategoryDao
import com.andersonlin.moneybook.data.model.Category

/** 分类仓库 */
class CategoryRepository(private val dao: CategoryDao) {

    fun getAllCategories() = dao.getAllCategories()

    fun getCategoriesByType(type: Int) = dao.getCategoriesByType(type)

    suspend fun getById(id: Long): Category? = dao.getById(id)

    suspend fun getCategoriesByTypeSnapshot(type: Int) = dao.getCategoriesByTypeSnapshot(type)

    suspend fun addCategory(category: Category): Long = dao.insert(category)

    suspend fun updateCategory(category: Category) = dao.update(category)

    suspend fun deleteCategory(category: Category) = dao.delete(category)

    suspend fun getAllSnapshot() = dao.getAllSnapshot()

    suspend fun insertAll(categories: List<Category>) = dao.insertAll(categories)

    suspend fun deleteAll() = dao.deleteAll()
}
