package com.andersonlin.moneybook.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.ui.account.AccountScreen
import com.andersonlin.moneybook.ui.bill.AddEditBillScreen
import com.andersonlin.moneybook.ui.bill.BillListScreen
import com.andersonlin.moneybook.ui.budget.BudgetScreen
import com.andersonlin.moneybook.ui.category.CategoryScreen
import com.andersonlin.moneybook.ui.categorybudget.CategoryBudgetScreen
import com.andersonlin.moneybook.ui.goal.GoalScreen
import com.andersonlin.moneybook.ui.home.HomeScreen
import com.andersonlin.moneybook.ui.ledger.LedgerScreen
import com.andersonlin.moneybook.ui.lock.LockSettingsScreen
import com.andersonlin.moneybook.ui.recurring.RecurringScreen
import com.andersonlin.moneybook.ui.reminder.ReminderScreen
import com.andersonlin.moneybook.ui.settings.AboutScreen
import com.andersonlin.moneybook.ui.settings.SettingsScreen
import com.andersonlin.moneybook.ui.stats.StatsScreen

/** 路由定义 */
object Routes {
    const val HOME = "home"
    const val BILLS = "bills"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val CATEGORIES = "categories"
    const val ACCOUNTS = "accounts"
    const val BUDGET = "budget"
    const val RECURRING = "recurring"
    const val LOCK = "lock"
    const val LEDGERS = "ledgers"
    const val GOALS = "goals"
    const val REMINDER = "reminder"
    const val CATEGORY_BUDGET = "category_budget"
    const val ABOUT = "about"
    const val ADD_EDIT = "add_edit?billId={billId}&type={type}&date={date}"

    /** billId 为 null 或 -1 表示新增；type 为新账单默认类型；date 为预填日期（0=今天） */
    fun addEdit(billId: Long? = null, type: Int = Bill.TYPE_EXPENSE, date: Long = 0L): String =
        "add_edit?billId=${billId ?: -1L}&type=$type&date=$date"
}

private data class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val topDestinations = listOf(
    TopDestination(Routes.HOME, "首页", Icons.Filled.Home),
    TopDestination(Routes.BILLS, "账单", Icons.AutoMirrored.Filled.ReceiptLong),
    TopDestination(Routes.STATS, "统计", Icons.Filled.PieChart),
    TopDestination(Routes.SETTINGS, "设置", Icons.Filled.Settings)
)

@Composable
fun AppRoot(addEvents: Flow<Unit> = emptyFlow()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = topDestinations.any { it.route == currentRoute }

    // 小组件「记一笔」等外部请求 → 跳转记一笔页面
    LaunchedEffect(Unit) {
        addEvents.collect {
            navController.navigate(Routes.addEdit())
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topDestinations.forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onAddBill = { navController.navigate(Routes.addEdit()) },
                    onViewAll = { navController.navigate(Routes.BILLS) },
                    onBillClick = { id -> navController.navigate(Routes.addEdit(billId = id)) },
                    onSetBudget = { navController.navigate(Routes.BUDGET) },
                    onManageLedgers = { navController.navigate(Routes.LEDGERS) },
                    onOpenGoals = { navController.navigate(Routes.GOALS) }
                )
            }
            composable(Routes.BILLS) {
                BillListScreen(
                    onAddBill = { navController.navigate(Routes.addEdit()) },
                    onBillClick = { id -> navController.navigate(Routes.addEdit(billId = id)) }
                )
            }
            composable(Routes.STATS) {
                StatsScreen(
                    onAddBillForDate = { date ->
                        navController.navigate(Routes.addEdit(date = date))
                    }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onManageCategories = { navController.navigate(Routes.CATEGORIES) },
                    onManageAccounts = { navController.navigate(Routes.ACCOUNTS) },
                    onManageBudget = { navController.navigate(Routes.BUDGET) },
                    onManageCategoryBudget = { navController.navigate(Routes.CATEGORY_BUDGET) },
                    onManageRecurring = { navController.navigate(Routes.RECURRING) },
                    onManageLedgers = { navController.navigate(Routes.LEDGERS) },
                    onManageGoals = { navController.navigate(Routes.GOALS) },
                    onManageReminder = { navController.navigate(Routes.REMINDER) },
                    onLockSettings = { navController.navigate(Routes.LOCK) },
                    onAbout = { navController.navigate(Routes.ABOUT) }
                )
            }
            composable(
                route = Routes.ADD_EDIT,
                arguments = listOf(
                    navArgument("billId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("type") {
                        type = NavType.IntType
                        defaultValue = Bill.TYPE_EXPENSE
                    },
                    navArgument("date") {
                        type = NavType.LongType
                        defaultValue = 0L
                    }
                )
            ) { entry ->
                val billId = entry.arguments?.getLong("billId") ?: -1L
                val type = entry.arguments?.getInt("type") ?: Bill.TYPE_EXPENSE
                val date = entry.arguments?.getLong("date") ?: 0L
                AddEditBillScreen(
                    billId = billId,
                    defaultType = type,
                    initialDate = date,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.CATEGORIES) {
                CategoryScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ACCOUNTS) {
                AccountScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.BUDGET) {
                BudgetScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CATEGORY_BUDGET) {
                CategoryBudgetScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.RECURRING) {
                RecurringScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LOCK) {
                LockSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LEDGERS) {
                LedgerScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.GOALS) {
                GoalScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.REMINDER) {
                ReminderScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
