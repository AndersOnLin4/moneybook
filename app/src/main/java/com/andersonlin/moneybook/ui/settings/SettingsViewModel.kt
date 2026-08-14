package com.andersonlin.moneybook.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.backup.BackupManager
import com.andersonlin.moneybook.data.model.Ledger
import com.andersonlin.moneybook.data.repository.LedgerRepository
import com.andersonlin.moneybook.data.settings.LockSettingsRepository
import com.andersonlin.moneybook.data.settings.SettingsRepository
import com.andersonlin.moneybook.data.settings.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsEvent {
    data class ShowMessage(val message: String) : SettingsEvent
    /** 导入加密备份需要密码（换机场景） */
    data class NeedPassword(val uri: Uri) : SettingsEvent
}

/** 设置页：主题切换、账本切换、加密备份导出 / 导入恢复、CSV 导出 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager,
    private val ledgerRepository: LedgerRepository,
    private val lockSettingsRepository: LockSettingsRepository
) : ViewModel() {

    /** 是否设置了应用锁（导出时需要输入锁密码） */
    val lockHasPin: StateFlow<Boolean> = lockSettingsRepository.settings
        .map { it.hasPin }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM
        )

    /** 账本列表（CSV 导出选账本用） */
    val ledgers: StateFlow<List<Ledger>> = ledgerRepository.getAllLedgers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events = _events.asSharedFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    /** 导出加密备份（.mbk：GZIP + AES-256-GCM，密钥为应用锁 PIN 明文派生，跨设备可恢复） */
    fun exportBackup(uri: Uri, pin: String?) {
        viewModelScope.launch {
            val result = backupManager.exportEncryptedTo(uri, pin)
            _events.emit(
                SettingsEvent.ShowMessage(
                    result.fold(
                        onSuccess = { "已导出加密备份，共 $it 条账单" },
                        onFailure = { "导出失败：${it.message ?: "未知错误"}" }
                    )
                )
            )
        }
    }

    /** 导出指定账本的账单为 CSV */
    fun exportCsv(uri: Uri, ledgerId: Long) {
        viewModelScope.launch {
            val result = backupManager.exportCsvTo(uri, ledgerId)
            _events.emit(
                SettingsEvent.ShowMessage(
                    result.fold(
                        onSuccess = { "CSV 导出成功，共 $it 条账单（Excel 可直接打开）" },
                        onFailure = { "导出失败：${it.message ?: "未知错误"}" }
                    )
                )
            )
        }
    }

    /** 导入恢复。加密备份密码不匹配且未提供密码时，发出 NeedPassword 事件请求用户输入 */
    fun importBackup(uri: Uri, pin: String? = null) {
        viewModelScope.launch {
            val result = backupManager.importFrom(uri, pin)
            result.fold(
                onSuccess = {
                    _events.emit(SettingsEvent.ShowMessage("导入成功，已恢复 $it 条账单（原有数据已覆盖）"))
                },
                onFailure = {
                    if (pin == null) {
                        _events.emit(SettingsEvent.NeedPassword(uri))
                    } else {
                        _events.emit(SettingsEvent.ShowMessage("导入失败：${it.message ?: "未知错误"}"))
                    }
                }
            )
        }
    }
}
