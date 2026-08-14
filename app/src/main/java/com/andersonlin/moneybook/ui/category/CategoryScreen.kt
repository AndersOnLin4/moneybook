package com.andersonlin.moneybook.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.ui.AppViewModelProvider
import com.andersonlin.moneybook.ui.components.CategoryIconBadge

/** 可选图标 */
val EMOJI_CHOICES = listOf(
    "🍜", "🚌", "🛍️", "🏨", "🎮", "💊", "💼", "🧧", "💰", "🍎",
    "☕", "📚", "🚕", "👗", "🐱", "🏠", "🎁", "✈️", "📱", "💄",
    "⚽", "🎬", "🧻", "🔧", "💡", "❤️", "📦", "🌹"
)

private data class PendingDelete(val category: Category, val billCount: Int)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryScreen(
    onBack: () -> Unit,
    viewModel: CategoryViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var addType by remember { mutableIntStateOf(Bill.TYPE_EXPENSE) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CategoryEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is CategoryEvent.AskDelete -> pendingDelete =
                    PendingDelete(event.category, event.billCount)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("分类管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val expenseCategories = categories.filter { it.type == Bill.TYPE_EXPENSE }
            val incomeCategories = categories.filter { it.type == Bill.TYPE_INCOME }

            item(key = "expense_header") {
                CategorySectionHeader(
                    title = "支出分类",
                    onAdd = {
                        addType = Bill.TYPE_EXPENSE
                        showAddDialog = true
                    }
                )
            }
            if (expenseCategories.isEmpty()) {
                item(key = "expense_empty") {
                    Text(
                        text = "暂无支出分类",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(expenseCategories, key = { "c${it.id}" }) { category ->
                    CategoryRow(
                        category = category,
                        onDelete = { viewModel.requestDelete(category) }
                    )
                }
            }

            item(key = "income_header") {
                CategorySectionHeader(
                    title = "收入分类",
                    onAdd = {
                        addType = Bill.TYPE_INCOME
                        showAddDialog = true
                    }
                )
            }
            if (incomeCategories.isEmpty()) {
                item(key = "income_empty") {
                    Text(
                        text = "暂无收入分类",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(incomeCategories, key = { "c${it.id}" }) { category ->
                    CategoryRow(
                        category = category,
                        onDelete = { viewModel.requestDelete(category) }
                    )
                }
            }

            item(key = "bottom_space") { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            type = addType,
            existing = categories,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, icon ->
                viewModel.addCategory(name, icon, addType)
                showAddDialog = false
            }
        )
    }

    pendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除分类") },
            text = {
                Text(
                    "「${pending.category.icon} ${pending.category.name}」分类下有 " +
                        "${pending.billCount} 笔账单，删除分类将同时删除这些账单。确定删除吗？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.confirmDelete(pending.category)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CategorySectionHeader(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = "添加分类")
        }
    }
}

@Composable
private fun CategoryRow(category: Category, onDelete: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryIconBadge(category.icon, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (category.isDefault) {
            Text(
                text = "默认",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        } else if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddCategoryDialog(
    type: Int,
    existing: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(EMOJI_CHOICES.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == Bill.TYPE_EXPENSE) "添加支出分类" else "添加收入分类") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 8) name = it },
                    label = { Text("分类名称") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Text("选择图标", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    EMOJI_CHOICES.forEach { emoji ->
                        val selected = emoji == icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                                .clickable { icon = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                if (existing.any { it.type == type && it.name == name.trim() }) return@TextButton
                onConfirm(name, icon)
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
