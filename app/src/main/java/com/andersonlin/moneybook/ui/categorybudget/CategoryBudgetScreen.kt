package com.andersonlin.moneybook.ui.categorybudget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andersonlin.moneybook.data.model.Category
import com.andersonlin.moneybook.ui.AppViewModelProvider
import com.andersonlin.moneybook.util.formatCents
import com.andersonlin.moneybook.util.monthLabel
import com.andersonlin.moneybook.util.toCents

/** 分类独立预算管理页 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBudgetScreen(
    onBack: () -> Unit,
    viewModel: CategoryBudgetViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingCategory by remember { mutableStateOf<Category?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CategoryBudgetEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("分类预算") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = viewModel::prevMonth) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
                }
                Text(
                    text = monthLabel(state.month),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = viewModel::nextMonth, enabled = state.canGoNext) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "下个月")
                }
            }
            Text(
                text = "给每个支出分类设置每月限额，用超了会红字提醒。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.expenseCategories, key = { it.id }) { category ->
                    val budget = state.budgetsForMonth[category.id]
                    val used = state.monthExpenses[category.id] ?: 0L
                    CategoryBudgetRow(
                        category = category,
                        budgetCents = budget?.amountCents,
                        usedCents = used,
                        onEdit = { editingCategory = category }
                    )
                }
                item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    editingCategory?.let { category ->
        BudgetEditDialog(
            category = category,
            currentBudget = state.budgetsForMonth[category.id]?.amountCents,
            onDismiss = { editingCategory = null },
            onApply = { cents ->
                viewModel.setBudget(category.id, cents)
                editingCategory = null
            }
        )
    }
}

@Composable
private fun CategoryBudgetRow(
    category: Category,
    budgetCents: Long?,
    usedCents: Long,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${category.icon} ${category.name}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onEdit) {
                    Text(if (budgetCents == null) "设预算" else "修改")
                }
            }
            val budget = budgetCents
            if (budget != null && budget > 0) {
                val ratio = (usedCents.toFloat() / budget).coerceIn(0f, 1f)
                val over = usedCents > budget
                val near = !over && usedCents >= budget * 0.8
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when {
                        over -> MaterialTheme.colorScheme.error
                        near -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(
                        text = "已用 ${formatCents(usedCents)} / ${formatCents(budget)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = when {
                            over -> "超支 ${formatCents(usedCents - budget)}"
                            near -> "接近预算"
                            else -> "剩余 ${formatCents(budget - usedCents)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            over -> MaterialTheme.colorScheme.error
                            near -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            } else {
                Text(
                    text = "本月已支出 ${formatCents(usedCents)}，未设置预算",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BudgetEditDialog(
    category: Category,
    currentBudget: Long?,
    onDismiss: () -> Unit,
    onApply: (Long) -> Unit
) {
    var amountText by remember {
        mutableStateOf(currentBudget?.let { (it / 100).toString() } ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${category.icon} ${category.name} 预算") },
        text = {
            Column {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { text ->
                        if (text.isBlank() || text.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?$"))) {
                            amountText = text
                        }
                    },
                    label = { Text("每月限额（元）") },
                    prefix = { Text("¥") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                TextButton(onClick = { onApply(0L) }) {
                    Text("清除该分类预算", color = MaterialTheme.colorScheme.outline)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = amountText.toCents() ?: return@TextButton
                onApply(cents)
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
