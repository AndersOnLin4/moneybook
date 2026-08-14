package com.andersonlin.moneybook.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andersonlin.moneybook.MainActivity
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.reminder.ReminderScheduler
import com.andersonlin.moneybook.data.settings.ThemeMode
import com.andersonlin.moneybook.ui.AppViewModelProvider
import com.andersonlin.moneybook.ui.recurring.cycleLabel

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val PAGE_COUNT = 6

/**
 * 首次引导：欢迎 → 账本 → 月度预算 → 周期账单 → 存钱目标 → 安全与外观。
 * 每一页都可以跳过；每一页的表单也可以直接完成对应配置。
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    var page by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is OnboardingEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onFinished) { Text("跳过全部") }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${page + 1} / $PAGE_COUNT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (page >= PAGE_COUNT - 1) onFinished() else page++
                    }
                ) {
                    Text(if (page >= PAGE_COUNT - 1) "开始使用" else "下一步")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
        ) {
            when (page) {
                0 -> WelcomePage()
                1 -> LedgerPage(viewModel)
                2 -> BudgetPage(viewModel)
                3 -> RecurringPage(viewModel)
                4 -> GoalPage(viewModel)
                5 -> SecurityPage(viewModel)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Spacer(Modifier.height(36.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun WelcomePage() {
    Spacer(Modifier.height(72.dp))
    Text(
        text = "💰",
        fontSize = 72.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(20.dp))
    Text(
        text = "欢迎使用记账本",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(28.dp))
    WelcomeFeature("🔒 零网络 · 零账号", "不联网、不登录，所有数据只保存在你的手机")
    WelcomeFeature("📊 图表化复盘", "饼图、趋势、日历热力图，钱花在哪一目了然")
    WelcomeFeature("🎯 预算与目标", "月度预算、分类限额、存钱目标，帮你管住手")
    WelcomeFeature("🔐 加密备份", "AES-256 加密导出，换机不丢数据")
    Spacer(Modifier.height(16.dp))
    Text(
        text = "接下来用 5 个小步骤完成初始配置，每一步都可以跳过。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun WelcomeFeature(title: String, desc: String) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LedgerPage(viewModel: OnboardingViewModel) {
    PageTitle("创建你的账本", "一个账本记一类生活：旅行、家庭、工作……已有「默认账本」，也可以再创建。")
    val ledgers by viewModel.ledgers.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    Column {
        ledgers.forEach { ledger ->
            Text(
                text = "${ledger.icon} ${ledger.name}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 8) name = it },
            label = { Text("新账本名称（如：旅行）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { viewModel.addLedger(name, "📒") { name = "" } },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("创建账本")
        }
    }
}

@Composable
private fun BudgetPage(viewModel: OnboardingViewModel) {
    PageTitle("设置月度预算", "给每个月的总支出设一个上限，首页会显示进度，超支自动提醒。")
    var amount by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(
            value = amount,
            onValueChange = { text ->
                if (text.isBlank() || text.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?$"))) amount = text
            },
            label = { Text("本月总支出预算") },
            prefix = { Text("¥") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { viewModel.setMonthBudget(amount) { amount = "" } },
            enabled = amount.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存预算")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringPage(viewModel: OnboardingViewModel) {
    PageTitle("周期账单", "房租、工资、会员订阅……设置一次，每月自动记账。")
    val categories by viewModel.expenseCategories.collectAsStateWithLifecycle()
    var amount by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var cycle by remember { mutableIntStateOf(com.andersonlin.moneybook.data.model.RecurringBill.CYCLE_MONTHLY) }
    Column {
        OutlinedTextField(
            value = amount,
            onValueChange = { text ->
                if (text.isBlank() || text.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?$"))) amount = text
            },
            label = { Text("金额") },
            prefix = { Text("¥") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text("分类", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            categories.forEach { category ->
                FilterChip(
                    selected = categoryId == category.id,
                    onClick = { categoryId = category.id },
                    label = { Text("${category.icon} ${category.name}") }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("重复周期", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            listOf(
                com.andersonlin.moneybook.data.model.RecurringBill.CYCLE_WEEKLY to "每周",
                com.andersonlin.moneybook.data.model.RecurringBill.CYCLE_MONTHLY to "每月",
                com.andersonlin.moneybook.data.model.RecurringBill.CYCLE_YEARLY to "每年"
            ).forEach { (value, label) ->
                FilterChip(selected = cycle == value, onClick = { cycle = value }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                viewModel.addRecurring(Bill.TYPE_EXPENSE, amount, categoryId, cycle) {
                    amount = ""
                }
            },
            enabled = amount.isNotBlank() && categoryId != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("添加周期账单")
        }
    }
}

@Composable
private fun GoalPage(viewModel: OnboardingViewModel) {
    PageTitle("存钱目标", "想买新手机？设个目标，自动算好每个月要存多少。")
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 10) name = it },
            label = { Text("目标名称（如：换新手机）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { text ->
                if (text.isBlank() || text.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?$"))) amount = text
            },
            label = { Text("目标金额（默认 6 个月达成）") },
            prefix = { Text("¥") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                viewModel.addGoal(name, amount) {
                    name = ""
                    amount = ""
                }
            },
            enabled = name.isNotBlank() && amount.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("创建目标")
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SecurityPage(viewModel: OnboardingViewModel) {
    PageTitle("安全与外观", "设置应用锁保护隐私，选择喜欢的主题。")
    val lockSettings by viewModel.lockSettings.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? MainActivity }
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var reminderEnabled by remember { mutableStateOf(false) }

    val biometricAvailable = remember {
        runCatching {
            BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            ) == BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)
    }

    Column {
        Text("应用锁密码（可选）", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
            label = { Text("密码（4-6 位数字）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pinConfirm,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinConfirm = it },
            label = { Text("确认密码") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (pin != pinConfirm) {
                    viewModel.emit("两次输入不一致")
                } else if (viewModel.setPin(pin)) {
                    pin = ""
                    pinConfirm = ""
                }
            },
            enabled = pin.length >= 4 && pinConfirm.length >= 4,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (lockSettings.hasPin) "修改密码" else "设置密码")
        }

        if (biometricAvailable && lockSettings.hasPin) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("指纹解锁", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = lockSettings.biometricEnabled,
                    onCheckedChange = viewModel::setBiometricEnabled
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("每日记账提醒（20:00）", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(
                checked = reminderEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            activity?.requestNotificationPermission { granted ->
                                if (granted) {
                                    reminderEnabled = true
                                    ReminderScheduler.schedule(context, 20, 0)
                                    viewModel.emit("记账提醒已开启，每天 20:00 提醒")
                                } else {
                                    viewModel.emit("未授予通知权限，无法开启提醒")
                                }
                            }
                        } else {
                            reminderEnabled = true
                            ReminderScheduler.schedule(context, 20, 0)
                            viewModel.emit("记账提醒已开启，每天 20:00 提醒")
                        }
                    } else {
                        reminderEnabled = false
                        ReminderScheduler.cancel(context)
                    }
                }
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("主题外观", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(
                ThemeMode.SYSTEM to "跟随系统",
                ThemeMode.LIGHT to "浅色",
                ThemeMode.DARK to "深色"
            ).forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                    label = { Text(label, fontSize = 13.sp) }
                )
            }
        }
    }
}
