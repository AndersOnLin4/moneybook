package com.andersonlin.moneybook

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.andersonlin.moneybook.data.settings.ThemeMode
import com.andersonlin.moneybook.ui.lock.LockScreen
import com.andersonlin.moneybook.ui.navigation.AppRoot
import com.andersonlin.moneybook.ui.theme.MoneyBookTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 入口 Activity。
 * 继承 FragmentActivity：BiometricPrompt（指纹解锁）要求宿主必须是 FragmentActivity。
 */
class MainActivity : FragmentActivity() {

    companion object {
        /** 小组件「记一笔」按钮携带的 Intent extra */
        const val EXTRA_OPEN_ADD = "extra_open_add"

        /** requestCode 合法上限（FragmentActivity 要求低 16 位） */
        private const val MAX_REQUEST_CODE = 0xFFFF
    }

    /**
     * HyperOS（小米）系统的「小窗/镜像」功能在某些入口会用超出 16 位的 requestCode
     * 调用旧版 startActivity 接口，触发 FragmentActivity 校验崩溃（
     * "Can only use lower 16 bits for requestCode"）。这里统一压缩到合法范围。
     */
    @Deprecated("Deprecated in Java")
    override fun startActivityForResult(intent: Intent, requestCode: Int) {
        super.startActivityForResult(intent, sanitizeRequestCode(requestCode))
    }

    override fun startActivityFromFragment(fragment: Fragment, intent: Intent, requestCode: Int) {
        super.startActivityFromFragment(fragment, intent, sanitizeRequestCode(requestCode))
    }

    private fun sanitizeRequestCode(requestCode: Int): Int =
        if (requestCode in 0..MAX_REQUEST_CODE) requestCode else requestCode and MAX_REQUEST_CODE

    private val lockVisible = mutableStateOf(false)
    private var wentBackground = true

    /** 小组件等外部入口请求打开「记一笔」页面 */
    val addRequestEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
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
                    else -> AppRoot(addEvents = addRequestEvents)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_ADD, false) == true) {
            intent.removeExtra(EXTRA_OPEN_ADD)
            addRequestEvents.tryEmit(Unit)
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
        // 刷新桌面小组件数据
        (application as MoneyBookApp).requestWidgetUpdate()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) wentBackground = true
    }
}
