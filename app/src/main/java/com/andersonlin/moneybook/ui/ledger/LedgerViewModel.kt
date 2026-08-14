package com.andersonlin.moneybook.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.model.Ledger
import com.andersonlin.moneybook.data.repository.BillRepository
import com.andersonlin.moneybook.data.repository.LedgerRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LedgerEvent {
    data class ShowMessage(val message: String) : LedgerEvent
    data class AskDelete(val ledger: Ledger, val billCount: Int) : LedgerEvent
}

/** 账本管理：增删账本、切换当前账本 */
class LedgerViewModel(
    private val ledgerRepository: LedgerRepository,
    private val billRepository: BillRepository
) : ViewModel() {

    val uiState: StateFlow<LedgerUiState> = combine(
        ledgerRepository.getAllLedgers(),
        ledgerRepository.activeLedgerId
    ) { ledgers, activeId ->
        LedgerUiState(ledgers = ledgers, activeLedgerId = activeId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerUiState()
    )

    private val _events = MutableSharedFlow<LedgerEvent>()
    val events = _events.asSharedFlow()

    fun addLedger(name: String, icon: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            emit(LedgerEvent.ShowMessage("账本名称不能为空"))
            return
        }
        viewModelScope.launch {
            val snapshot = ledgerRepository.getAllSnapshot()
            if (snapshot.any { it.name == trimmed }) {
                _events.emit(LedgerEvent.ShowMessage("「$trimmed」账本已存在"))
                return@launch
            }
            ledgerRepository.addLedger(trimmed, icon)
            _events.emit(LedgerEvent.ShowMessage("已创建账本「$trimmed」"))
        }
    }

    fun setActive(id: Long) {
        viewModelScope.launch {
            ledgerRepository.setActiveLedger(id)
        }
    }

    fun requestDelete(ledger: Ledger) {
        if (ledger.id == Ledger.DEFAULT_ID) {
            emit(LedgerEvent.ShowMessage("默认账本不可删除"))
            return
        }
        viewModelScope.launch {
            val count = billRepository.countByLedger(ledger.id)
            _events.emit(LedgerEvent.AskDelete(ledger, count))
        }
    }

    fun confirmDelete(ledger: Ledger) {
        viewModelScope.launch {
            ledgerRepository.deleteLedger(ledger)
                .onSuccess { _events.emit(LedgerEvent.ShowMessage("已删除账本「${ledger.name}」")) }
                .onFailure { _events.emit(LedgerEvent.ShowMessage(it.message ?: "删除失败")) }
        }
    }

    private fun emit(event: LedgerEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}

data class LedgerUiState(
    val ledgers: List<Ledger> = emptyList(),
    val activeLedgerId: Long = 1L
)
