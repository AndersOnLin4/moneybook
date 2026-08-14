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
import com.andersonlin.moneybook.data.settings.LockSettingsRepository
import com.andersonlin.moneybook.util.formatCentsPlain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份管理：
 * - 明文 JSON 导出/导入（保留兼容旧版备份）
 * - 加密备份 v2（.mbk）：ZIP 容器（backup.json + attachments/<账单id>.<扩展名> 附件原文件）
 *   → GZIP 压缩 → AES-256-GCM 加密（系统内置库，零第三方依赖），密钥由应用锁 PIN 派生（未设锁时用内置密钥）。
 * - 导入自动识别：.mbk v2（含附件打包）/ v1（旧加密）/ 旧 .json 明文。
 * 通过系统文件选择器（SAF）读写 Uri，无需任何权限。
 */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val lockSettingsRepository: LockSettingsRepository
) {

    companion object {
        /** 加密备份文件头标识：v3（密钥由 PIN 明文派生，跨设备可恢复） */
        private val MAGIC_V3 = byteArrayOf('M'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), '3'.code.toByte())

        /** 旧版加密备份文件头（v2：ZIP 含附件，密钥绑定本机盐，仅同设备可恢复） */
        private val MAGIC_V2 = byteArrayOf('M'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), '2'.code.toByte())

        /** 旧版加密备份文件头（v1，无附件打包） */
        private val MAGIC_V1 = byteArrayOf('M'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte())

        /** 未设置应用锁时的内置派生密钥盐 */
        private const val DEFAULT_KEY_SALT = "moneybook-default-key-v1"

        /** v3 密钥派生盐：SHA-256(PIN + 本盐)，与设备无关，跨设备可重现 */
        private const val KEY_SALT_V3 = "moneybook-key-salt-v3"
    }

    /** 备份密码错误（导入时用于触发密码输入弹窗） */
    class BackupPasswordException : Exception("应用锁密码不匹配或文件已损坏")

    /** 构建完整备份 JSON（全部账本、分类、账户、预算、周期账单、存钱目标、账单） */
    private suspend fun buildBackupJson(): Pair<String, Int> {
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
                    put("attachmentUri", b.attachmentUri ?: JSONObject.NULL)
                    put("attachmentMime", b.attachmentMime ?: JSONObject.NULL)
                })
            }
        })
        return root.toString(2) to bills.size
    }

    /** 导出明文 JSON（兼容旧版） */
    suspend fun exportTo(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val (text, count) = buildBackupJson()
            val output = context.contentResolver.openOutputStream(uri)
                ?: error("无法打开输出流")
            output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            count
        }
    }

    /**
     * 导出加密备份 v3（.mbk）：ZIP 容器（backup.json + 附件原文件）→ GZIP → AES-256-GCM。
     * 密钥由应用锁 PIN 明文派生（跨设备可恢复）；未设锁时用内置密钥。
     * 已设锁时要求传入正确 PIN。
     */
    suspend fun exportEncryptedTo(uri: Uri, pin: String?): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val lockSettings = lockSettingsRepository.settings.first()
            if (lockSettings.hasPin) {
                if (pin == null) {
                    error("需要输入应用锁密码才能导出")
                }
                if (!lockSettingsRepository.verifyPin(pin, lockSettings)) {
                    error("应用锁密码不正确")
                }
            }
            val key = deriveKeyV3(if (lockSettings.hasPin) pin else null)
            val (text, count) = buildBackupJson()
            val attachments = collectAttachments()
            val zipBytes = buildZipContainer(text, attachments)
            val compressed = gzip(zipBytes)
            val encrypted = encryptAesGcm(compressed, key)
            val output = context.contentResolver.openOutputStream(uri)
                ?: error("无法打开输出流")
            output.use { it.write(MAGIC_V3 + encrypted) }
            count
        }
    }

    private data class AttachmentPayload(
        val billId: Long,
        val ext: String,
        val data: ByteArray
    )

    /** 读取全部账单附件内容（读取失败或为空的附件跳过） */
    private suspend fun collectAttachments(): List<AttachmentPayload> {
        val bills = database.billDao().getAllSnapshot()
        return bills.mapNotNull { b ->
            val uriString = b.attachmentUri ?: return@mapNotNull null
            runCatching {
                val data = context.contentResolver
                    .openInputStream(Uri.parse(uriString))?.use { it.readBytes() }
                if (data != null && data.isNotEmpty()) {
                    AttachmentPayload(b.id, safeExtension(uriString, b.attachmentMime), data)
                } else {
                    null
                }
            }.getOrNull()
        }
    }

    private fun buildZipContainer(json: String, attachments: List<AttachmentPayload>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            attachments.forEach { a ->
                zip.putNextEntry(ZipEntry("attachments/${a.billId}${a.ext}"))
                zip.write(a.data)
                zip.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun readZipContainer(zipBytes: ByteArray): Pair<String, List<AttachmentPayload>> {
        var json: String? = null
        val attachments = mutableListOf<AttachmentPayload>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val data = zis.readBytes()
                when {
                    entry.name == "backup.json" -> json = data.toString(Charsets.UTF_8)
                    entry.name.startsWith("attachments/") -> {
                        val fileName = entry.name.removePrefix("attachments/")
                        val id = fileName.substringBefore('.').toLongOrNull()
                        val ext = fileName.substringAfter('.', "")
                            .let { if (it.isEmpty()) ".bin" else ".$it" }
                        if (id != null) attachments.add(AttachmentPayload(id, ext, data))
                    }
                }
                zis.closeEntry()
            }
        }
        return (json ?: error("备份文件损坏：缺少 backup.json")) to attachments
    }

    private fun safeExtension(uriString: String, mime: String?): String {
        // 1. 从 Uri 文件名取扩展名
        val fromName = uriString.substringAfterLast('/', "")
            .substringAfterLast('.', "")
            .takeIf { it.length in 1..6 && it.all { c -> c.isLetterOrDigit() } }
        // 2. 从 MIME 映射
        val fromMime = when (mime) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            else -> null
        }
        // 3. 从系统查询 DISPLAY_NAME
        val fromDisplay = runCatching {
            context.contentResolver.query(
                Uri.parse(uriString),
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.substringAfterLast('.', "")
            ?.takeIf { it.length in 1..6 && it.all { c -> c.isLetterOrDigit() } }
        // 4. 图片兜底 jpg，其它 bin
        val fallback = if (mime?.startsWith("image/") == true) "jpg" else "bin"
        return "." + (fromName ?: fromMime ?: fromDisplay ?: fallback)
    }

    private fun mimeFromExt(ext: String): String = when (ext.lowercase()) {
        ".jpg", ".jpeg" -> "image/jpeg"
        ".png" -> "image/png"
        ".webp" -> "image/webp"
        ".gif" -> "image/gif"
        ".pdf" -> "application/pdf"
        ".txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    /** 旧版密钥派生（v1/v2，绑定本机盐，仅同设备恢复用）：SHA-256(pinHash + 固定盐) */
    private suspend fun deriveKeyV1(): SecretKeySpec {
        val lockSettings = lockSettingsRepository.settings.first()
        val secret = when {
            lockSettings.hasPin -> lockSettings.pinHash + ":" + DEFAULT_KEY_SALT
            else -> DEFAULT_KEY_SALT
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(secret.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    /** v3 密钥派生：SHA-256(PIN 明文 + 固定盐)，与设备无关，跨设备可重现 */
    private fun deriveKeyV3(pin: String?): SecretKeySpec {
        val secret = (pin ?: DEFAULT_KEY_SALT) + KEY_SALT_V3
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(secret.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    private fun encryptAesGcm(data: ByteArray, key: SecretKeySpec): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(data)
    }

    private fun decryptAesGcm(payload: ByteArray, key: SecretKeySpec): ByteArray {
        val iv = payload.copyOfRange(0, 12)
        val cipherText = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherText)
    }

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }

    /**
     * 从指定 Uri 读取备份并覆盖恢复，返回恢复的账单条数。
     * 自动识别：.mbk v3（跨设备，pin 可选）/ v2 / v1 / 旧 .json。
     * v3 导入成功后，若本机尚未设置应用锁且用户提供了 PIN，则自动用该 PIN 设置应用锁。
     */
    suspend fun importFrom(uri: Uri, pin: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("无法打开文件")
            val bytes = input.use { it.readBytes() }

            fun decryptPayload(payload: ByteArray, key: SecretKeySpec): ByteArray = try {
                decryptAesGcm(payload, key)
            } catch (e: Exception) {
                throw BackupPasswordException()
            }

            val restored: List<Bill>
            when {
                bytes.size > MAGIC_V3.size &&
                    bytes.copyOfRange(0, MAGIC_V3.size).contentEquals(MAGIC_V3) -> {
                    // v3：密钥由 PIN 明文派生。尝试顺序：用户输入 PIN → 内置密钥
                    val payload = bytes.copyOfRange(MAGIC_V3.size, bytes.size)
                    val candidates = mutableListOf<String?>()
                    if (pin != null) candidates += pin
                    candidates += null
                    var plain: ByteArray? = null
                    for (candidate in candidates) {
                        plain = try {
                            decryptAesGcm(payload, deriveKeyV3(candidate))
                        } catch (e: Exception) {
                            null
                        }
                        if (plain != null) break
                    }
                    val zipBytes = gunzip(plain ?: throw BackupPasswordException())
                    val (text, attachments) = readZipContainer(zipBytes)
                    restored = restoreFromJson(text)
                    writeAttachments(attachments, restored)
                    // 换机场景：本机未设锁且用户输入了密码 → 自动同步为应用锁密码
                    if (pin != null && !lockSettingsRepository.settings.first().hasPin) {
                        lockSettingsRepository.setPin(pin)
                    }
                }
                bytes.size > MAGIC_V2.size &&
                    bytes.copyOfRange(0, MAGIC_V2.size).contentEquals(MAGIC_V2) -> {
                    // v2：旧密钥（绑定本机盐，仅同设备）
                    val zipBytes = gunzip(
                        decryptPayload(
                            bytes.copyOfRange(MAGIC_V2.size, bytes.size),
                            deriveKeyV1()
                        )
                    )
                    val (text, attachments) = readZipContainer(zipBytes)
                    restored = restoreFromJson(text)
                    writeAttachments(attachments, restored)
                }
                bytes.size > MAGIC_V1.size &&
                    bytes.copyOfRange(0, MAGIC_V1.size).contentEquals(MAGIC_V1) -> {
                    // v1：旧加密 JSON（无附件打包）
                    val text = gunzip(
                        decryptPayload(
                            bytes.copyOfRange(MAGIC_V1.size, bytes.size),
                            deriveKeyV1()
                        )
                    ).toString(Charsets.UTF_8)
                    restored = restoreFromJson(text)
                }
                else -> {
                    // 旧版明文 JSON
                    restored = restoreFromJson(bytes.toString(Charsets.UTF_8))
                }
            }
            restored.size
        }
    }

    /** 把备份中的附件写入应用私有目录，并更新账单附件地址。
     *  MIME 优先采用账单 JSON 里的图片类型（兼容旧版 .bin 扩展名丢失问题）。 */
    private suspend fun writeAttachments(attachments: List<AttachmentPayload>, bills: List<Bill>) {
        if (attachments.isEmpty()) return
        val billMimeMap = bills.associate { it.id to it.attachmentMime }
        val dir = File(context.filesDir, "attachments").apply { mkdirs() }
        attachments.forEach { a ->
            val file = File(dir, "${a.billId}${a.ext}")
            file.writeBytes(a.data)
            val jsonMime = billMimeMap[a.billId]
            val finalMime = when {
                jsonMime?.startsWith("image/") == true ->
                    if (jsonMime == "image/*") "image/jpeg" else jsonMime
                else -> mimeFromExt(a.ext)
            }
            database.billDao().updateAttachment(a.billId, file.toURI().toString(), finalMime)
        }
    }

    /** 解析 JSON 并在事务内整体覆盖恢复，返回恢复的账单列表 */
    private suspend fun restoreFromJson(text: String): List<Bill> {
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
        return bills
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
