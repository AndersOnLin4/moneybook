package com.andersonlin.moneybook.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andersonlin.moneybook.data.settings.ThemeMode
import com.andersonlin.moneybook.ui.AppViewModelProvider
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onManageCategories: () -> Unit,
    onAbout: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showImportConfirm by remember { mutableStateOf(false) }

    val exportFileName = "moneybook_backup_" +
        LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".json"

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportBackup(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importBackup(it) } }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { CenterAlignedTopAppBar(title = { Text("设置") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSectionHeader("外观")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    ThemeOptionRow("跟随系统", themeMode == ThemeMode.SYSTEM) {
                        viewModel.setThemeMode(ThemeMode.SYSTEM)
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ThemeOptionRow("浅色模式", themeMode == ThemeMode.LIGHT) {
                        viewModel.setThemeMode(ThemeMode.LIGHT)
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ThemeOptionRow("深色模式", themeMode == ThemeMode.DARK) {
                        viewModel.setThemeMode(ThemeMode.DARK)
                    }
                }
            }

            SettingsSectionHeader("数据")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    SettingsRow(Icons.Filled.Category, "分类管理", onManageCategories)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        Icons.Filled.Upload,
                        "导出备份（JSON）"
                    ) { exportLauncher.launch(exportFileName) }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsRow(Icons.Filled.Download, "导入恢复备份") {
                        showImportConfirm = true
                    }
                }
            }

            SettingsSectionHeader("其他")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    SettingsRow(Icons.Filled.Info, "关于", onAbout)
                }
            }

            Text(
                text = "版本 1.0.0 · 所有数据仅保存在本机，应用不申请网络权限",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("导入备份") },
            text = { Text("导入备份将覆盖当前所有账单和分类数据，且无法撤销。确定继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    importLauncher.launch(
                        arrayOf(
                            "application/json",
                            "text/json",
                            "text/plain",
                            "application/octet-stream"
                        )
                    )
                }) { Text("继续") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun ThemeOptionRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}
