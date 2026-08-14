package com.andersonlin.moneybook.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.andersonlin.moneybook.data.db.AppDatabase
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.util.formatCentsPlain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * JSON 备份管理：导出 / 导入恢复。
 * 通过系统文件选择器（SAF）读写 Uri，无需任何权限。
 */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase
) {

    /** 导出全部账单与分类为 JSON 到指定 Uri，返回账单条数 */
    suspend fun exportTo(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val bills = database.billDao().getAllSnapshot()
            val categories = database.categoryDao().getAllSnapshot()

            val root = JSONObject()
            root.put("app", "moneybook")
            root.put("version", 1)
            root.put("exportedAt", LocalDateTime.now().toString())

            val categoryArray = JSONArray()
            categories.forEach { c ->
                categoryArray.put(
                    JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("type", c.type)
                        put("icon", c.icon)
                        put("isDefault", c.isDefault)
                        put("sortOrder", c.sortOrder)
                    }
                )
            }
            root.put("categories", categoryArray)

            val billArray = JSONArray()
            bills.forEach { b ->
                billArray.put(
                    JSONObject().apply {
                        put("id", b.id)
                        put("type", b.type)
                        put("amountCents", b.amountCents)
                        put("categoryId", b.categoryId)
                        put("note", b.note)
                        put("dateEpochDay", b.dateEpochDay)
                        put("createdAt", b.createdAt)
                    }
                )
            }
            root.put("bills", billArray)

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
            val bills = parseBills(root.getJSONArray("bills"))

            require(categories.isNotEmpty() && categories.all { it.id > 0L }) {
                "备份文件损坏：分类数据无效"
            }
            require(categories.all { it.type == 0 || it.type == 1 }) {
                "备份文件损坏：分类类型无效"
            }
            require(bills.all { it.id > 0L }) { "备份文件损坏：账单数据无效" }
            require(bills.all { it.type == 0 || it.type == 1 }) {
                "备份文件损坏：账单类型无效"
            }
            require(bills.all { it.amountCents > 0L }) { "备份文件损坏：存在无效金额" }
            val categoryIds = categories.map { it.id }.toSet()
            require(bills.all { it.categoryId in categoryIds }) {
                "备份文件损坏：账单引用了不存在的分类"
            }

            database.withTransaction {
                database.billDao().deleteAll()
                database.categoryDao().deleteAll()
                database.categoryDao().insertAll(categories)
                database.billDao().insertAll(bills)
            }
            bills.size
        }
    }

    /** 导出全部账单为 CSV（带 UTF-8 BOM，Excel 可直接打开），返回账单条数 */
    suspend fun exportCsvTo(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val bills = database.billDao().getAllSnapshot().sortedByDescending { it.dateEpochDay }
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
                    note = o.optString("note", ""),
                    dateEpochDay = o.getLong("dateEpochDay"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }
        return list
    }
}
