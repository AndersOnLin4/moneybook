package com.andersonlin.moneybook.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.model.Account
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.data.model.RecurringBill
import com.andersonlin.moneybook.data.repository.AccountRepository
import com.andersonlin.moneybook.data.repository.CategoryRepository
import com.andersonlin.moneybook.data.repository.RecurringRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecurringListUiState(
    val items: List<RecurringBill> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
    val accounts: Map<Long, Account> = emptyMap()
)

sealed interface RecurringEvent {
    data class ShowMessage(val message: String) : RecurringEvent
}

/** 周期账单：新增 / 编辑 / 删除 / 启用停用；到期账单在 App 启动时自动补记 */
class RecurringViewModel(
    private val recurringRepository: RecurringRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    val uiState: StateFlow<RecurringListUiState> = combine(
        recurringRepository.getAll(),
        categoryRepository.getAllCategories(),
        accountRepository.getAllAccounts()
    ) { items, categories, accounts ->
        RecurringListUiState(
            items = items,
            categories = categories.associateBy { it.id },
            accounts = accounts.associateBy { it.id }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecurringListUiState()
    )

    private val _events = MutableSharedFlow<RecurringEvent>()
    val events = _events.asSharedFlow()

    fun add(item: RecurringBill) {
        viewModelScope.launch {
            recurringRepository.add(item)
            _events.emit(RecurringEvent.ShowMessage("已添加周期账单"))
        }
    }

    fun update(item: RecurringBill) {
        viewModelScope.launch {
            recurringRepository.update(item)
            _events.emit(RecurringEvent.ShowMessage("已更新周期账单"))
        }
    }

    fun delete(item: RecurringBill) {
        viewModelScope.launch {
            recurringRepository.delete(item)
            _events.emit(RecurringEvent.ShowMessage("已删除周期账单"))
        }
    }

    fun toggleEnabled(item: RecurringBill, enabled: Boolean) {
        viewModelScope.launch {
            recurringRepository.update(item.copy(enabled = enabled))
        }
    }
}
