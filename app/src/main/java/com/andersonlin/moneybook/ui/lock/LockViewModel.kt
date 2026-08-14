package com.andersonlin.moneybook.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.settings.LockSettings
import com.andersonlin.moneybook.data.settings.LockSettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LockEvent {
    data class ShowMessage(val message: String) : LockEvent
    data object Unlocked : LockEvent
}

/** 应用锁：启用/停用、设置 PIN、验证 PIN */
class LockViewModel(
    private val repository: LockSettingsRepository
) : ViewModel() {

    val settings: StateFlow<LockSettings> = repository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LockSettings()
        )

    private val _events = MutableSharedFlow<LockEvent>()
    val events = _events.asSharedFlow()

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(enabled)
            _events.emit(LockEvent.ShowMessage(if (enabled) "应用锁已开启" else "应用锁已关闭"))
        }
    }

    /** 设置新 PIN；返回是否合法并已保存 */
    fun setPin(pin: String): Boolean {
        if (pin.length !in 4..6 || !pin.all { it.isDigit() }) {
            emit(LockEvent.ShowMessage("密码需为 4-6 位数字"))
            return false
        }
        viewModelScope.launch {
            repository.setPin(pin)
            _events.emit(LockEvent.ShowMessage("密码已设置"))
        }
        return true
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setBiometricEnabled(enabled) }
    }

    /** 验证 PIN；成功后发出 Unlocked 事件 */
    fun verifyPin(pin: String) {
        val current = settings.value
        if (!current.hasPin) {
            emit(LockEvent.ShowMessage("尚未设置密码"))
            return
        }
        if (repository.verifyPin(pin, current)) {
            emit(LockEvent.Unlocked)
        } else {
            emit(LockEvent.ShowMessage("密码错误，请重试"))
        }
    }

    private fun emit(event: LockEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}
