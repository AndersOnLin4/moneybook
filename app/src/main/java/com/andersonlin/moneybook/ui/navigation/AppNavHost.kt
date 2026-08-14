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
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.ui.account.AccountScreen
import com.andersonlin.moneybook.ui.bill.AddEditBillScreen
import com.andersonlin.moneybook.ui.bill.BillListScreen
import com.andersonlin.moneybook.ui.budget.BudgetScreen
import com.andersonlin.moneybook.ui.category.CategoryScreen
import com.andersonlin.moneybook.ui.home.HomeScreen
import com.andersonlin.moneybook.ui.recurring.RecurringScreen
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
    const val ABOUT = "about"
    const val ADD_EDIT = "add_edit?billId={billId}&type={type}"

    /** billId 为 null 或 -1 表示新增；type 为新账单默认类型（支出/收入） */
    fun addEdit(billId: Long? = null, type: Int = Bill.TYPE_EXPENSE): String =
        "add_edit?billId=${billId ?: -1L}&type=$type"
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
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = topDestinations.any { it.route == currentRoute }

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
                    onSetBudget = { navController.navigate(Routes.BUDGET) }
                )
            }
            composable(Routes.BILLS) {
                BillListScreen(
                    onAddBill = { navController.navigate(Routes.addEdit()) },
                    onBillClick = { id -> navController.navigate(Routes.addEdit(billId = id)) }
                )
            }
            composable(Routes.STATS) {
                StatsScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onManageCategories = { navController.navigate(Routes.CATEGORIES) },
                    onManageAccounts = { navController.navigate(Routes.ACCOUNTS) },
                    onManageBudget = { navController.navigate(Routes.BUDGET) },
                    onManageRecurring = { navController.navigate(Routes.RECURRING) },
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
                    }
                )
            ) { entry ->
                val billId = entry.arguments?.getLong("billId") ?: -1L
                val type = entry.arguments?.getInt("type") ?: Bill.TYPE_EXPENSE
                AddEditBillScreen(
                    billId = billId,
                    defaultType = type,
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
            composable(Routes.RECURRING) {
                RecurringScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
