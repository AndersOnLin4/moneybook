package com.andersonlin.moneybook.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.andersonlin.moneybook.data.db.AppDatabase
import com.andersonlin.moneybook.data.model.Account
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.data.model.DefaultLedgers
import com.andersonlin.moneybook.data.model.Goal
import com.andersonlin.moneybook.data.model.Ledger
import com.andersonlin.moneybook.data.model.RecurringBill
import com.andersonlin.moneybook.util.formatCentsPlain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * JSON 备份管理：导出 / 导入恢复（包含分类、账户、账本、预算、周期账单、存钱目标与全部账单）。
 * 兼容 v1.x 旧备份：无 ledgers/goals/recurring 字段时自动补默认账本并归入账单。
 * 通过系统文件选择器（SAF）读写 Uri，无需任何权限。
 */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase
) {

    /** 导出全部数据为 JSON 到指定 Uri，返回账单条数 */
    suspend fun exportTo(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val bills = database.billDao().getAllSnapshot()
            val categories = database.categoryDao().getAllSnapshot()
            val accounts = database.accountDao().getAllSnapshot()
            val ledgers = database.ledgerDao().getAllSnapshot()
            val recurring = database.recurringBillDao().getAllSnapshot()
            val goals = database.goalDao().getAllSnapshot()

            val root = JSONObject()
            root.put("app", "moneybook")
            root.put("version", 2)
            root.put("exportedAt", LocalDateTime.now().toString())

            root.put("categories", JSONArray().apply {
                categories.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id); put("name", c.name); put("type", c.type)
                        put("icon", c.icon); put("isDefault", c.isDefault); put("sortOrder", c.sortOrder)
                    })
                }
            })
            root.put("accounts", JSONArray().apply {
                accounts.forEach { a ->
                    put(JSONObject().apply {
                        put("id", a.id); put("name", a.name); put("icon", a.icon)
                        put("isDefault", a.isDefault); put("sortOrder", a.sortOrder)
                    })
                }
            })
            root.put("ledgers", JSONArray().apply {
                ledgers.forEach { l ->
                    put(JSONObject().apply {
                        put("id", l.id); put("name", l.name); put("icon", l.icon)
                        put("sortOrder", l.sortOrder); put("createdAt", l.createdAt)
                    })
                }
            })
            root.put("recurring", JSONArray().apply {
                recurring.forEach { r ->
                    put(JSONObject().apply {
                        put("id", r.id); put("type", r.type); put("amountCents", r.amountCents)
                        put("categoryId", r.categoryId); put("accountId", r.accountId)
                        put("ledgerId", r.ledgerId); put("note", r.note); put("cycle", r.cycle)
                        put("startEpochDay", r.startEpochDay)
                        put("lastGeneratedEpochDay", r.lastGeneratedEpochDay)
                        put("enabled", r.enabled)
                    })
                }
            })
            root.put("goals", JSONArray().apply {
                goals.forEach { g ->
                    put(JSONObject().apply {
                        put("id", g.id); put("name", g.name); put("icon", g.icon)
                        put("targetCents", g.targetCents); put("savedCents", g.savedCents)
                        put("deadlineEpochDay", g.deadlineEpochDay); put("createdAt", g.createdAt)
                    })
                }
            })
            root.put("bills", JSONArray().apply {
                bills.forEach { b ->
                    put(JSONObject().apply {
                        put("id", b.id); put("type", b.type); put("amountCents", b.amountCents)
                        put("categoryId", b.categoryId); put("accountId", b.accountId)
                        put("ledgerId", b.ledgerId); put("note", b.note)
                        put("dateEpochDay", b.dateEpochDay); put("createdAt", b.createdAt)
                    })
                }
            })

            val text = root.toString(2)
            val output = context.contentResolver.openOutputStream(uri)
                ?: error("无法打开输出流")
            output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            bills.size
        }
    }

    /** 从指定 Uri 读取 JSON 并覆盖恢复，返回恢复的账单条数 */
    suspend fun importFrom(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("无法打开文件")
            val text = input.use { it.readBytes().toString(Charsets.UTF_8) }

            val root = JSONObject(text)
            if (root.optString("app") != "moneybook" && !root.has("bills")) {
                error("不是有效的记账本备份文件")
            }

            val categories = parseCategories(root.getJSONArray("categories"))
            val accounts = parseAccounts(root.getJSONArray("accounts"))
            // 旧备份无 ledgers：补默认账本
            val ledgers = if (root.has("ledgers")) {
                parseLedgers(root.getJSONArray("ledgers"))
            } else {
                listOf(DefaultLedgers.DEFAULT.copy(id = Bill.DEFAULT_LEDGER_ID))
            }
            val bills = parseBills(root.getJSONArray("bills"))
            val recurring = if (root.has("recurring")) {
                parseRecurring(root.getJSONArray("recurring"))
            } else {
                emptyList()
            }
            val goals = if (root.has("goals")) parseGoals(root.getJSONArray("goals")) else emptyList()

            require(categories.isNotEmpty() && categories.all { it.id > 0L }) { "备份文件损坏：分类数据无效" }
            require(accounts.isNotEmpty() && accounts.all { it.id > 0L }) { "备份文件损坏：账户数据无效" }
            require(ledgers.isNotEmpty() && ledgers.all { it.id > 0L }) { "备份文件损坏：账本数据无效" }
            require(bills.all { it.id > 0L && it.amountCents > 0L }) { "备份文件损坏：账单数据无效" }
            val categoryIds = categories.map { it.id }.toSet()
            val accountIds = accounts.map { it.id }.toSet()
            val ledgerIds = ledgers.map { it.id }.toSet()
            require(bills.all { it.categoryId in categoryIds }) { "备份文件损坏：账单引用了不存在的分类" }
            require(bills.all { it.accountId in accountIds }) { "备份文件损坏：账单引用了不存在的账户" }
            require(bills.all { it.ledgerId in ledgerIds }) { "备份文件损坏：账单引用了不存在的账本" }
            require(recurring.all { it.categoryId in categoryIds && it.accountId in accountIds && it.ledgerId in ledgerIds }) {
                "备份文件损坏：周期账单引用无效"
            }

            database.withTransaction {
                database.billDao().deleteAll()
                database.recurringBillDao().deleteAll()
                database.goalDao().deleteAll()
                database.categoryDao().deleteAll()
                database.accountDao().deleteAll()
                database.ledgerDao().deleteAll()

                database.ledgerDao().insertAll(ledgers)
                database.categoryDao().insertAll(categories)
                database.accountDao().insertAll(accounts)
                database.billDao().insertAll(bills)
                database.recurringBillDao().insertAll(recurring)
                database.goalDao().insertAll(goals)
            }
            bills.size
        }
    }

    /** 导出指定账本的账单为 CSV（带 UTF-8 BOM，Excel 可直接打开），返回账单条数 */
    suspend fun exportCsvTo(uri: Uri, ledgerId: Long): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val bills = database.billDao().getSnapshotForLedger(ledgerId)
                .sortedByDescending { it.dateEpochDay }
            val categories = database.categoryDao().getAllSnapshot().associateBy { it.id }
            val accounts = database.accountDao().getAllSnapshot().associateBy { it.id }

            val sb = StringBuilder()
            sb.append('\uFEFF') // BOM：让 Excel 正确识别 UTF-8 中文
            sb.append("日期,类型,分类,账户,金额,备注\n")
            bills.forEach { b ->
                val date = LocalDate.ofEpochDay(b.dateEpochDay).toString()
                val type = if (b.type == Bill.TYPE_EXPENSE) "支出" else "收入"
                val category = categories[b.categoryId]?.name ?: ""
                val account = accounts[b.accountId]?.name ?: ""
                val amount = (if (b.type == Bill.TYPE_EXPENSE) "-" else "+") +
                    formatCentsPlain(b.amountCents)
                val line = listOf(date, type, category, account, amount, b.note)
                    .joinToString(",") { csvEscape(it) }
                sb.append(line).append('\n')
            }

            val output = context.contentResolver.openOutputStream(uri)
                ?: error("无法打开输出流")
            output.use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
            bills.size
        }
    }

    /** CSV 字段转义：含逗号/引号/换行时用双引号包裹 */
    private fun csvEscape(value: String): String {
        return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun parseCategories(array: JSONArray): List<Category> {
        val list = mutableListOf<Category>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                Category(
                    id = o.optLong("id", 0L),
                    name = o.getString("name"),
                    type = o.getInt("type"),
                    icon = o.optString("icon", "💰"),
                    isDefault = o.optBoolean("isDefault", false),
                    sortOrder = o.optInt("sortOrder", 0)
                )
            )
        }
        return list
    }

    private fun parseAccounts(array: JSONArray): List<Account> {
        val list = mutableListOf<Account>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                Account(
                    id = o.optLong("id", 0L),
                    name = o.getString("name"),
                    icon = o.optString("icon", "💵"),
                    isDefault = o.optBoolean("isDefault", false),
                    sortOrder = o.optInt("sortOrder", 0)
                )
            )
        }
        return list
    }

    private fun parseLedgers(array: JSONArray): List<Ledger> {
        val list = mutableListOf<Ledger>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                Ledger(
                    id = o.optLong("id", 0L),
                    name = o.getString("name"),
                    icon = o.optString("icon", "📒"),
                    sortOrder = o.optInt("sortOrder", 0),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }
        return list
    }

    private fun parseBills(array: JSONArray): List<Bill> {
        val list = mutableListOf<Bill>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                Bill(
                    id = o.optLong("id", 0L),
                    type = o.getInt("type"),
                    amountCents = o.getLong("amountCents"),
                    categoryId = o.getLong("categoryId"),
                    accountId = o.optLong("accountId", Bill.DEFAULT_ACCOUNT_ID),
                    ledgerId = o.optLong("ledgerId", Bill.DEFAULT_LEDGER_ID),
                    note = o.optString("note", ""),
                    dateEpochDay = o.getLong("dateEpochDay"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }
        return list
    }

    private fun parseRecurring(array: JSONArray): List<RecurringBill> {
        val list = mutableListOf<RecurringBill>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                RecurringBill(
                    id = o.optLong("id", 0L),
                    type = o.getInt("type"),
                    amountCents = o.getLong("amountCents"),
                    categoryId = o.getLong("categoryId"),
                    accountId = o.optLong("accountId", Bill.DEFAULT_ACCOUNT_ID),
                    ledgerId = o.optLong("ledgerId", Bill.DEFAULT_LEDGER_ID),
                    note = o.optString("note", ""),
                    cycle = o.getInt("cycle"),
                    startEpochDay = o.getLong("startEpochDay"),
                    lastGeneratedEpochDay = o.getLong("lastGeneratedEpochDay"),
                    enabled = o.optBoolean("enabled", true)
                )
            )
        }
        return list
    }

    private fun parseGoals(array: JSONArray): List<Goal> {
        val list = mutableListOf<Goal>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                Goal(
                    id = o.optLong("id", 0L),
                    name = o.getString("name"),
                    icon = o.optString("icon", "🎯"),
                    targetCents = o.getLong("targetCents"),
                    savedCents = o.optLong("savedCents", 0L),
                    deadlineEpochDay = o.getLong("deadlineEpochDay"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }
        return list
    }
}
