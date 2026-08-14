package com.andersonlin.moneybook.ui.bill

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.ui.AppViewModelProvider
import com.andersonlin.moneybook.ui.components.BillRow
import com.andersonlin.moneybook.ui.components.EmptyState
import com.andersonlin.moneybook.util.formatCents
import com.andersonlin.moneybook.util.monthLabel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BillListScreen(
    onAddBill: () -> Unit,
    onBillClick: (Long) -> Unit,
    viewModel: BillListViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("账单") },
                actions = {
                    IconButton(onClick = onAddBill) {
                        Icon(Icons.Filled.Add, contentDescription = "记一笔")
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
            OutlinedTextField(
                value = state.keyword,
                onValueChange = viewModel::setKeyword,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("搜索备注或分类") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.keyword.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setKeyword("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp)
            )

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.typeFilter == BillFilter.TYPE_ALL,
                    onClick = { viewModel.setTypeFilter(BillFilter.TYPE_ALL) },
                    label = { Text("全部") }
                )
                FilterChip(
                    selected = state.typeFilter == Bill.TYPE_EXPENSE,
                    onClick = { viewModel.setTypeFilter(Bill.TYPE_EXPENSE) },
                    label = { Text("支出") }
                )
                FilterChip(
                    selected = state.typeFilter == Bill.TYPE_INCOME,
                    onClick = { viewModel.setTypeFilter(Bill.TYPE_INCOME) },
                    label = { Text("收入") }
                )
            }

            if (state.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.List,
                        title = if (state.keyword.isNotBlank()) "没有找到相关账单" else "暂无账单",
                        subtitle = if (state.keyword.isBlank()) "点击右上角 + 记一笔" else null
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    state.items.forEach { row ->
                        when (row) {
                            is BillListRow.Header -> stickyHeader(key = "header_${row.month}") {
                                MonthHeaderRow(row)
                            }
                            is BillListRow.Item -> item(key = "bill_${row.bill.id}") {
                                BillRow(
                                    bill = row.bill,
                                    category = row.category,
                                    showDate = false,
                                    onClick = { onBillClick(row.bill.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeaderRow(header: BillListRow.Header) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = monthLabel(header.month),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "支出 ${formatCents(header.expense)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "收入 ${formatCents(header.income)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
