package com.andersonlin.moneybook.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andersonlin.moneybook.ui.AppViewModelProvider
import com.andersonlin.moneybook.ui.components.BillRow
import com.andersonlin.moneybook.ui.components.EmptyState
import com.andersonlin.moneybook.util.formatCents
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddBill: () -> Unit,
    onViewAll: () -> Unit,
    onBillClick: (Long) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { CenterAlignedTopAppBar(title = { Text("记账本") }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddBill,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "记一笔")
            }
        }
    ) { padding ->
        if (state.recentBills.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Filled.AccountBalanceWallet,
                    title = "还没有账单",
                    subtitle = "点击右下角 + 记一笔吧"
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                item(key = "summary") {
                    MonthSummaryCard(
                        month = state.month,
                        income = state.income,
                        expense = state.expense
                    )
                }
                item(key = "recent_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "最近账单",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onViewAll) { Text("全部") }
                    }
                }
                items(state.recentBills, key = { it.id }) { bill ->
                    BillRow(
                        bill = bill,
                        category = state.categories[bill.categoryId],
                        onClick = { onBillClick(bill.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthSummaryCard(month: YearMonth, income: Long, expense: Long) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "${month.year}年${month.monthValue}月",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "结余 " + formatCents(income - expense),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "收入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "+" + formatCents(income),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "支出",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "-" + formatCents(expense),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
