package com.andersonlin.moneybook.ui.bill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.model.Account
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.data.repository.AccountRepository
import com.andersonlin.moneybook.data.repository.BillRepository
import com.andersonlin.moneybook.data.repository.CategoryRepository
import com.andersonlin.moneybook.util.formatCentsPlain
import com.andersonlin.moneybook.util.toCents
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AddEditUiState(
    val isEdit: Boolean = false,
    val type: Int = Bill.TYPE_EXPENSE,
    val amountText: String = "",
    val note: String = "",
    val dateEpochDay: Long = LocalDate.now().toEpochDay(),
    val categoryId: Long? = null,
    val categories: List<Category> = emptyList(),
    val accountId: Long = Bill.DEFAULT_ACCOUNT_ID,
    val accounts: List<Account> = emptyList(),
    val amountError: Boolean = false
)

sealed interface AddEditEvent {
    data class ShowMessage(val message: String) : AddEditEvent
    data object Saved : AddEditEvent
    data object SavedContinue : AddEditEvent
    data object Deleted : AddEditEvent
}

/** 记一笔：新增 / 编辑 / 删除账单 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddEditBillViewModel(
    private val billRepository: BillRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AddEditEvent>()
    val events = _events.asSharedFlow()

    private var bill: Bill? = null
    private var initialized = false

    /** 进入页面时调用：billId > 0 为编辑模式，否则为新增 */
    fun init(billId: Long, defaultType: Int) {
        if (initialized) return
        initialized = true

        // 当前类型的分类列表，类型切换后自动选中第一个分类。
        // 携带 type 一起发射，避免旧类型的列表迟到后覆盖新类型状态。
        viewModelScope.launch {
            _uiState
                .map { it.type }
                .distinctUntilChanged()
                .flatMapLatest { type ->
                    categoryRepository.getCategoriesByType(type).map { type to it }
                }
                .collect { (type, categories) ->
                    _uiState.update { state ->
                        if (state.type != type) {
                            // 迟到的旧类型列表，忽略，避免覆盖 categoryId
                            state.copy(categories = categories)
                        } else {
                            val categoryId = if (state.categoryId != null &&
                                categories.any { it.id == state.categoryId }
                            ) {
                                state.categoryId
                            } else {
                                categories.firstOrNull()?.id
                            }
                            state.copy(categories = categories, categoryId = categoryId)
                        }
                    }
                }
        }

        // 账户列表（不分收支类型），默认选中第一个账户
        viewModelScope.launch {
            accountRepository.getAllAccounts().collect { accounts ->
                _uiState.update { state ->
                    val accountId = if (accounts.any { it.id == state.accountId }) {
                        state.accountId
                    } else {
                        accounts.firstOrNull()?.id ?: Bill.DEFAULT_ACCOUNT_ID
                    }
                    state.copy(accounts = accounts, accountId = accountId)
                }
            }
        }

        if (billId > 0) {
            viewModelScope.launch {
                billRepository.getById(billId)?.let { b ->
                    bill = b
                    _uiState.update {
                        it.copy(
                            isEdit = true,
                            type = b.type,
                            amountText = formatCentsPlain(b.amountCents),
                            note = b.note,
                            dateEpochDay = b.dateEpochDay,
                            categoryId = b.categoryId,
                            accountId = b.accountId
                        )
                    }
                }
            }
        } else {
            _uiState.update { it.copy(isEdit = false, type = defaultType) }
        }
    }

    fun setType(type: Int) = _uiState.update { it.copy(type = type, categoryId = null) }

    fun setAccount(id: Long) = _uiState.update { it.copy(accountId = id) }

    fun setAmount(text: String) {
        // 只允许数字 + 最多一个小数点、两位小数
        if (text.isBlank() || text.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?$"))) {
            _uiState.update { it.copy(amountText = text, amountError = false) }
        }
    }

    fun setNote(text: String) = _uiState.update { it.copy(note = text.take(50)) }

    fun setDate(epochDay: Long) = _uiState.update { it.copy(dateEpochDay = epochDay) }

    fun setCategory(id: Long) = _uiState.update { it.copy(categoryId = id) }

    /**
     * 保存账单。
     * @param andContinue true = 保存后留在本页并清空金额/备注（连续记账），
     *                    false = 保存后返回上一页。
     */
    fun save(andContinue: Boolean = false) {
        val state = _uiState.value
        val cents = state.amountText.toCents()
        if (cents == null) {
            _uiState.update { it.copy(amountError = true) }
            emit(AddEditEvent.ShowMessage("请输入有效金额（大于 0，最多两位小数）"))
            return
        }
        val categoryId = state.categoryId
        if (categoryId == null) {
            emit(AddEditEvent.ShowMessage("请先选择分类"))
            return
        }
        viewModelScope.launch {
            val existing = bill
            if (existing == null) {
                billRepository.insert(
                    Bill(
                        type = state.type,
                        amountCents = cents,
                        categoryId = categoryId,
                        accountId = state.accountId,
                        note = state.note.trim(),
                        dateEpochDay = state.dateEpochDay
                    )
                )
            } else {
                billRepository.update(
                    existing.copy(
                        type = state.type,
                        amountCents = cents,
                        categoryId = categoryId,
                        accountId = state.accountId,
                        note = state.note.trim(),
                        dateEpochDay = state.dateEpochDay
                    )
                )
            }
            if (andContinue) {
                // 保留类型/分类/账户/日期，清空金额与备注，方便连续记账
                _uiState.update {
                    it.copy(amountText = "", note = "", amountError = false)
                }
                bill = null
                _events.emit(AddEditEvent.SavedContinue)
            } else {
                _events.emit(AddEditEvent.Saved)
            }
        }
    }

    fun delete() {
        val existing = bill ?: return
        viewModelScope.launch {
            billRepository.delete(existing)
            _events.emit(AddEditEvent.Deleted)
        }
    }

    private fun emit(event: AddEditEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}
