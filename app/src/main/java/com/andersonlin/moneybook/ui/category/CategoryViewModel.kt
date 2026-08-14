package com.andersonlin.moneybook.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.data.repository.BillRepository
import com.andersonlin.moneybook.data.repository.CategoryRepository
import com.andersonlin.moneybook.data.repository.RecurringRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CategoryEvent {
    data class ShowMessage(val message: String) : CategoryEvent
    data class AskDelete(val category: Category, val billCount: Int) : CategoryEvent
}

/** 分类管理：内置分类不可删除，自定义分类可增删 */
class CategoryViewModel(
    private val categoryRepository: CategoryRepository,
    private val billRepository: BillRepository,
    private val recurringRepository: RecurringRepository
) : ViewModel() {

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _events = MutableSharedFlow<CategoryEvent>()
    val events = _events.asSharedFlow()

    fun addCategory(name: String, icon: String, type: Int) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            emit(CategoryEvent.ShowMessage("分类名称不能为空"))
            return
        }
        viewModelScope.launch {
            val snapshot = categoryRepository.getCategoriesByTypeSnapshot(type)
            if (snapshot.any { it.name == trimmed }) {
                _events.emit(CategoryEvent.ShowMessage("「$trimmed」分类已存在"))
                return@launch
            }
            val maxOrder = snapshot.maxOfOrNull { it.sortOrder } ?: 0
            categoryRepository.addCategory(
                Category(
                    name = trimmed,
                    type = type,
                    icon = icon,
                    isDefault = false,
                    sortOrder = maxOrder + 1
                )
            )
            _events.emit(CategoryEvent.ShowMessage("已添加「$trimmed」"))
        }
    }

    /** 删除自定义分类：有账单时先弹确认，无账单直接删除 */
    fun requestDelete(category: Category) {
        if (category.isDefault) {
            emit(CategoryEvent.ShowMessage("默认分类不可删除"))
            return
        }
        viewModelScope.launch {
            val count = billRepository.countByCategory(category.id)
            if (count > 0) {
                _events.emit(CategoryEvent.AskDelete(category, count))
            } else {
                categoryRepository.deleteCategory(category)
                _events.emit(CategoryEvent.ShowMessage("已删除「${category.name}」"))
            }
        }
    }

    fun confirmDelete(category: Category) {
        viewModelScope.launch {
            billRepository.deleteByCategory(category.id)
            recurringRepository.deleteByCategory(category.id)
            categoryRepository.deleteCategory(category)
            _events.emit(CategoryEvent.ShowMessage("已删除「${category.name}」及其账单"))
        }
    }

    private fun emit(event: CategoryEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}
