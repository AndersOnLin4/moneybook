package com.andersonlin.moneybook.data.repository

import com.andersonlin.moneybook.data.db.GoalDao
import com.andersonlin.moneybook.data.model.Goal

/** 存钱目标仓库 */
class GoalRepository(private val dao: GoalDao) {

    fun getAllGoals() = dao.getAllGoals()

    suspend fun getById(id: Long): Goal? = dao.getById(id)

    suspend fun add(goal: Goal): Long = dao.insert(goal)

    suspend fun update(goal: Goal) = dao.update(goal)

    suspend fun delete(goal: Goal) = dao.delete(goal)

    /** 存入一笔：追加已存金额 */
    suspend fun deposit(goalId: Long, cents: Long) {
        val goal = dao.getById(goalId) ?: return
        dao.update(goal.copy(savedCents = goal.savedCents + cents))
    }

    suspend fun getAllSnapshot() = dao.getAllSnapshot()

    suspend fun insertAll(goals: List<Goal>) = dao.insertAll(goals)

    suspend fun deleteAll() = dao.deleteAll()
}
