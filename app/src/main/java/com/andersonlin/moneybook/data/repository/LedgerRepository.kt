package com.andersonlin.moneybook.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.andersonlin.moneybook.data.db.AppDatabase
import com.andersonlin.moneybook.data.db.LedgerDao
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.Ledger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.ledgerDataStore by preferencesDataStore(name = "ledger_settings")

/**
 * 账本仓库：账本增删改 + 当前活动账本（DataStore 持久化）。
 * 账单按账本隔离；删除账本会连带删除其账单（默认账本不可删）。
 */
class LedgerRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val dao: LedgerDao
) {

    private val activeLedgerKey = longPreferencesKey("active_ledger_id")

    /** 当前活动账本 id（默认 1） */
    val activeLedgerId: Flow<Long> = context.ledgerDataStore.data.map { prefs ->
        prefs[activeLedgerKey] ?: Bill.DEFAULT_LEDGER_ID
    }

    suspend fun getActiveLedgerId(): Long =
        context.ledgerDataStore.data.first()[activeLedgerKey] ?: Bill.DEFAULT_LEDGER_ID

    suspend fun setActiveLedger(id: Long) {
        context.ledgerDataStore.edit { it[activeLedgerKey] = id }
    }

    fun getAllLedgers() = dao.getAllLedgers()

    suspend fun getAllSnapshot(): List<Ledger> = dao.getAllSnapshot()

    suspend fun getById(id: Long): Ledger? = dao.getById(id)

    /** 新增账本，排到末尾 */
    suspend fun addLedger(name: String, icon: String): Long {
        val snapshot = dao.getAllSnapshot()
        val maxOrder = snapshot.maxOfOrNull { it.sortOrder } ?: 0
        return dao.insert(Ledger(name = name, icon = icon, sortOrder = maxOrder + 1))
    }

    /**
     * 删除账本：连带删除其账单与周期账单；默认账本不可删；至少保留一个账本。
     * 若删除的是当前活动账本，自动切换到默认账本。
     */
    suspend fun deleteLedger(ledger: Ledger): Result<Unit> {
        if (ledger.id == Ledger.DEFAULT_ID) {
            return Result.failure(IllegalStateException("默认账本不可删除"))
        }
        val all = dao.getAllSnapshot()
        if (all.size <= 1) {
            return Result.failure(IllegalStateException("至少需要保留一个账本"))
        }
        return runCatching {
            database.withTransaction {
                database.billDao().deleteByLedger(ledger.id)
                database.recurringBillDao().deleteByLedger(ledger.id)
                dao.delete(ledger)
            }
            if (getActiveLedgerId() == ledger.id) {
                setActiveLedger(Bill.DEFAULT_LEDGER_ID)
            }
        }
    }
}
