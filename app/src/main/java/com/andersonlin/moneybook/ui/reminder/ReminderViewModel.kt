package com.andersonlin.moneybook.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.reminder.ReminderRepository
import com.andersonlin.moneybook.data.reminder.ReminderSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ReminderEvent {
    data class ShowMessage(val message: String) : ReminderEvent
}

/** 记账提醒设置 */
class ReminderViewModel(
    private val repository: ReminderRepository
) : ViewModel() {

    val settings: StateFlow<ReminderSettings> = repository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReminderSettings()
        )

    private val _events = MutableSharedFlow<ReminderEvent>()
    val events = _events.asSharedFlow()

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(enabled) }
    }

    fun setTime(hour: Int, minute: Int) {
        viewModelScope.launch { repository.setTime(hour, minute) }
    }

    fun emit(message: String) {
        viewModelScope.launch { _events.emit(ReminderEvent.ShowMessage(message)) }
    }
}
