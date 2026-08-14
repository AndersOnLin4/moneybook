package com.andersonlin.moneybook.ui.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andersonlin.moneybook.ui.AppViewModelProvider

/** 从任意 Context 向上找 Activity（LocalContext 可能被包装） */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 解锁页：数字键盘 + 指纹（若启用且可用） */
@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    viewModel: LockViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val pinLength = settings.pinLength.coerceAtLeast(4)
    val activity = remember(context) { context.findActivity() as? FragmentActivity }

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
                is LockEvent.ShowMessage -> {
                    if (event.message.contains("密码错误")) {
                        error = "密码错误，请重试"
                        entered = ""
                    } else {
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
                LockEvent.Unlocked -> onUnlocked()
            }
        }
    }

    // 输入位数达到设定长度时自动验证
    LaunchedEffect(entered) {
        if (entered.length >= pinLength) {
            viewModel.verifyPin(entered)
        }
    }

    // 已设置密码且启用指纹时，进入锁屏自动唤起指纹验证（无需点击）
    LaunchedEffect(settings) {
        if (activity != null && settings.hasPin && settings.biometricEnabled) {
            showBiometricPrompt(
                activity = activity,
                onError = { error = it },
                onSuccess = onUnlocked
            )
        }
    }

    fun append(digit: Char) {
        if (entered.length < 6) {
            entered += digit
            error = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = error ?: "输入密码",
                style = MaterialTheme.typography.titleMedium,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(pinLength) { index ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                if (index < entered.length) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                    )
                }
            }

            if (biometricAvailable && settings.biometricEnabled) {
                Spacer(Modifier.height(28.dp))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        )
                        .clickable {
                            if (activity == null) {
                                error = "无法启动指纹验证（Activity 不可用）"
                            } else {
                                error = null
                                showBiometricPrompt(
                                    activity = activity,
                                    onError = { error = it },
                                    onSuccess = onUnlocked
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Fingerprint,
                        contentDescription = "指纹解锁",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "指纹解锁",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))
            Keypad(
                onDigit = ::append,
                onBackspace = {
                    entered = entered.dropLast(1)
                    error = null
                }
            )
        }
    }
}

@Composable
private fun Keypad(onDigit: (Char) -> Unit, onBackspace: () -> Unit) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.size(64.dp))
                    } else if (key == "⌫") {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clickable(onClick = onBackspace),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                )
                                .clickable { onDigit(key[0]) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * 启动指纹验证（防御式：任何失败都会通过 onError 反馈到界面，不静默吞掉）。
 */
private fun showBiometricPrompt(
    activity: FragmentActivity,
    onError: (String) -> Unit,
    onSuccess: () -> Unit
) {
    val executor = runCatching { activity.mainExecutor }
        .getOrElse { ContextCompat.getMainExecutor(activity) }

    val prompt = runCatching {
        BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError("指纹验证失败：$errString（$errorCode）")
                }
            }
        )
    }.getOrElse {
        onError("无法启动指纹验证：${it.message}")
        return
    }

    val info = runCatching {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("解锁记账本")
            .setNegativeButtonText("取消")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
    }.getOrElse {
        onError("无法创建指纹请求：${it.message}")
        return
    }

    runCatching { prompt.authenticate(info) }
        .onFailure { onError("指纹验证启动失败：${it.message}") }
}
