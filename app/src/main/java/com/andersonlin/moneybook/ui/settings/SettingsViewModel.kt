package com.andersonlin.moneybook.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.backup.BackupManager
import com.andersonlin.moneybook.data.settings.SettingsRepository
import com.andersonlin.moneybook.data.settings.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsEvent {
    data class ShowMessage(val message: String) : SettingsEvent
}

/** 设置页：主题切换、JSON 备份导出 / 导入恢复 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM
        )

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events = _events.asSharedFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val result = backupManager.exportTo(uri)
            _events.emit(
                SettingsEvent.ShowMessage(
                    result.fold(
                        onSuccess = { "导出成功，共 $it 条账单" },
                        onFailure = { "导出失败：${it.message ?: "未知错误"}" }
                    )
                )
            )
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            val result = backupManager.exportCsvTo(uri)
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

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val result = backupManager.importFrom(uri)
            _events.emit(
                SettingsEvent.ShowMessage(
                    result.fold(
                        onSuccess = { "导入成功，已恢复 $it 条账单（原有数据已覆盖）" },
                        onFailure = { "导入失败：${it.message ?: "未知错误"}" }
                    )
                )
            )
        }
    }
}
