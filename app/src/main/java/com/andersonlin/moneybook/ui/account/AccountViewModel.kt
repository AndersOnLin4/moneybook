package com.andersonlin.moneybook.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.model.Account
import com.andersonlin.moneybook.data.repository.AccountRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AccountEvent {
    data class ShowMessage(val message: String) : AccountEvent
    data class AskDelete(val account: Account) : AccountEvent
}

/** 账户管理：增删账户；删除时账单自动转移到其它账户 */
class AccountViewModel(
    private val accountRepository: AccountRepository
) : ViewModel() {

    val accounts: StateFlow<List<Account>> = accountRepository.getAllAccounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _events = MutableSharedFlow<AccountEvent>()
    val events = _events.asSharedFlow()

    fun addAccount(name: String, icon: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            emit(AccountEvent.ShowMessage("账户名称不能为空"))
            return
        }
        viewModelScope.launch {
            val snapshot = accountRepository.getAllSnapshot()
            if (snapshot.any { it.name == trimmed }) {
                _events.emit(AccountEvent.ShowMessage("「$trimmed」账户已存在"))
                return@launch
            }
            accountRepository.addAccount(trimmed, icon)
            _events.emit(AccountEvent.ShowMessage("已添加「$trimmed」"))
        }
    }

    fun requestDelete(account: Account) {
        if (accounts.value.size <= 1) {
            emit(AccountEvent.ShowMessage("至少需要保留一个账户"))
            return
        }
        emit(AccountEvent.AskDelete(account))
    }

    fun confirmDelete(account: Account) {
        viewModelScope.launch {
            accountRepository.deleteAccount(account)
                .onSuccess { _events.emit(AccountEvent.ShowMessage("已删除「${account.name}」")) }
                .onFailure { _events.emit(AccountEvent.ShowMessage(it.message ?: "删除失败")) }
        }
    }

    private fun emit(event: AccountEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}
