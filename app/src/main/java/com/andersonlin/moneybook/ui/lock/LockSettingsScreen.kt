package com.andersonlin.moneybook.ui.lock

import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andersonlin.moneybook.ui.AppViewModelProvider

/** 应用锁设置：启用/停用、设置密码、指纹解锁 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockSettingsScreen(
    onBack: () -> Unit,
    viewModel: LockViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showPinDialog by remember { mutableStateOf(false) }
    var pendingEnable by remember { mutableStateOf(false) }

    val biometricAvailable = remember {
        runCatching {
            BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            ) == BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LockEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                LockEvent.Unlocked -> Unit
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("应用锁") },
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
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    SwitchRow(
                        title = "启用应用锁",
                        checked = settings.enabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !settings.hasPin) {
                                pendingEnable = true
                                showPinDialog = true
                            } else {
                                viewModel.setEnabled(enabled)
                            }
                        }
                    )
                    if (settings.hasPin) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "修改密码",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { showPinDialog = true }) {
                                Text("修改")
                            }
                        }
                    }
                    if (biometricAvailable) {
                        SwitchRow(
                            title = "指纹解锁",
                            checked = settings.biometricEnabled,
                            onCheckedChange = viewModel::setBiometricEnabled
                        )
                    }
                }
            }
            Text(
                text = "开启后，每次打开 App（包括从后台返回）都需要验证密码或指纹。" +
                    if (!biometricAvailable) "\n本机未检测到可用的指纹硬件。" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }

    if (showPinDialog) {
        SetPinDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = { pin ->
                if (viewModel.setPin(pin)) {
                    showPinDialog = false
                    if (pendingEnable) {
                        pendingEnable = false
                        viewModel.setEnabled(true)
                    }
                }
            }
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SetPinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置密码") },
        text = {
            Column {
                OutlinedTextField(
                    value = first,
                    onValueChange = {
                        if (it.length <= 6 && it.all { c -> c.isDigit() }) first = it
                    },
                    label = { Text("密码（4-6 位数字）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = second,
                    onValueChange = {
                        if (it.length <= 6 && it.all { c -> c.isDigit() }) second = it
                    },
                    label = { Text("确认密码") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error != null,
                    singleLine = true
                )
                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    first.length !in 4..6 -> error = "密码需为 4-6 位数字"
                    first != second -> error = "两次输入不一致"
                    else -> onConfirm(first)
                }
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
