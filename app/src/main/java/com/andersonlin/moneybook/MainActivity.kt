package com.andersonlin.moneybook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.andersonlin.moneybook.data.settings.ThemeMode
import com.andersonlin.moneybook.ui.lock.LockScreen
import com.andersonlin.moneybook.ui.navigation.AppRoot
import com.andersonlin.moneybook.ui.theme.MoneyBookTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val lockVisible = mutableStateOf(false)
    private var wentBackground = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MoneyBookApp
        val settingsRepository = app.settingsRepository
        val lockSettingsRepository = app.lockSettingsRepository

        setContent {
            val themeMode by settingsRepository.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val lockEnabled by lockSettingsRepository.settings
                .map { it.enabled }
                .collectAsStateWithLifecycle(initialValue = null)

            MoneyBookTheme(themeMode = themeMode) {
                when {
                    // 设置尚未读取完成，先显示空白避免闪屏
                    lockEnabled == null -> Box(Modifier.fillMaxSize())
                    lockVisible.value && lockEnabled == true -> {
                        LockScreen(onUnlocked = { lockVisible.value = false })
                    }
                    else -> AppRoot()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 每次回到前台（含冷启动）都要求解锁；屏幕旋转不重复锁定
        if (wentBackground && !isChangingConfigurations) {
            lifecycleScope.launch {
                if ((application as MoneyBookApp).lockSettingsRepository
                        .settings.first().enabled
                ) {
                    lockVisible.value = true
                }
            }
        }
        wentBackground = false
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) wentBackground = true
    }
}
