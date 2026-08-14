package com.andersonlin.moneybook.data.repository

import androidx.room.withTransaction
import com.andersonlin.moneybook.data.db.AccountDao
import com.andersonlin.moneybook.data.db.AppDatabase
import com.andersonlin.moneybook.data.model.Account
import com.andersonlin.moneybook.data.model.Bill

/** 账户仓库：增删账户；删除账户时其账单与周期账单自动转移到默认账户（现金） */
class AccountRepository(
    private val database: AppDatabase,
    private val dao: AccountDao
) {

    fun getAllAccounts() = dao.getAllAccounts()

    suspend fun getAllSnapshot(): List<Account> = dao.getAllSnapshot()

    suspend fun getById(id: Long): Account? = dao.getById(id)

    /** 新增账户，排到末尾 */
    suspend fun addAccount(name: String, icon: String): Long {
        val snapshot = dao.getAllSnapshot()
        val maxOrder = snapshot.maxOfOrNull { it.sortOrder } ?: 0
        return dao.insert(Account(name = name, icon = icon, isDefault = false, sortOrder = maxOrder + 1))
    }

    /**
     * 删除账户：账单与周期账单转移到默认账户（现金）。
     * 若删除的是现金账户本身，则转移到剩余的第一个账户；禁止删除最后一个账户。
     */
    suspend fun deleteAccount(account: Account): Result<Unit> {
        val all = dao.getAllSnapshot()
        if (all.size <= 1) {
            return Result.failure(IllegalStateException("至少需要保留一个账户"))
        }
        val targetId = if (account.id == Bill.DEFAULT_ACCOUNT_ID) {
            (all.firstOrNull { it.id != account.id } ?: return Result.failure(IllegalStateException("至少需要保留一个账户"))).id
        } else {
            Bill.DEFAULT_ACCOUNT_ID
        }
        return runCatching {
            database.withTransaction {
                database.billDao().reassignAccount(account.id, targetId)
                database.recurringBillDao().reassignAccount(account.id, targetId)
                dao.delete(account)
            }
        }
    }
}
