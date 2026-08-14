package com.andersonlin.moneybook

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.andersonlin.moneybook.data.settings.ThemeMode
import com.andersonlin.moneybook.ui.lock.LockScreen
import com.andersonlin.moneybook.ui.navigation.AppRoot
import com.andersonlin.moneybook.ui.onboarding.OnboardingScreen
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

        /** 通知权限请求的固定 requestCode（绕开 activity 库的随机 requestCode 缺陷） */
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 0x1111

        /** 创建文档（导出）的固定 requestCode */
        private const val CREATE_DOCUMENT_REQUEST_CODE = 0x2222

        /** 打开文档（导入）的固定 requestCode */
        private const val OPEN_DOCUMENT_REQUEST_CODE = 0x3333

        /** 相册选图（账单附件）的固定 requestCode */
        private const val PICK_IMAGE_REQUEST_CODE = 0x4444

        /** 附件文件选择（账单附件）的固定 requestCode */
        private const val ATTACHMENT_REQUEST_CODE = 0x5555
    }

    private var pendingPermissionCallback: ((Boolean) -> Unit)? = null
    private var pendingCreateDocumentCallback: ((android.net.Uri?) -> Unit)? = null
    private var pendingOpenDocumentCallback: ((android.net.Uri?) -> Unit)? = null
    private var pendingPickImageCallback: ((android.net.Uri?) -> Unit)? = null
    private var pendingAttachmentCallback: ((android.net.Uri?) -> Unit)? = null

    /**
     * 请求通知权限。使用固定的小 requestCode 直接调用，绕开
     * androidx.activity 1.9.0 权限请求路径生成超大随机 requestCode 的缺陷
     * （HyperOS 上会导致 "Can only use lower 16 bits for requestCode" 崩溃）。
     */
    fun requestNotificationPermission(callback: (Boolean) -> Unit) {
        pendingPermissionCallback = callback
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            NOTIFICATION_PERMISSION_REQUEST_CODE
        )
    }

    /** 弹出系统「另存为」对话框创建文档（导出用），固定 requestCode 避免库缺陷 */
    fun createDocument(mimeType: String, suggestedName: String, callback: (android.net.Uri?) -> Unit) {
        pendingCreateDocumentCallback = callback
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, suggestedName)
        }
        startActivityForResult(intent, CREATE_DOCUMENT_REQUEST_CODE)
    }

    /** 弹出系统文件选择器（导入用），固定 requestCode 避免库缺陷；不过滤类型以兼容 .mbk/.json */
    fun openDocument(callback: (android.net.Uri?) -> Unit) {
        pendingOpenDocumentCallback = callback
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, OPEN_DOCUMENT_REQUEST_CODE)
    }

    /** 唤起系统相册选择图片（截图/照片附件） */
    fun pickImage(callback: (android.net.Uri?) -> Unit) {
        pendingPickImageCallback = callback
        val intent = Intent(
            if (Build.VERSION.SDK_INT >= 33) {
                android.provider.MediaStore.ACTION_PICK_IMAGES
            } else {
                Intent.ACTION_GET_CONTENT
            }
        ).apply {
            type = "image/*"
        }
        runCatching { startActivityForResult(intent, PICK_IMAGE_REQUEST_CODE) }
            .onFailure {
                pendingPickImageCallback = null
                callback(null)
            }
    }

    /** 唤起系统文件选择器选择附件（任意文件） */
    fun pickAttachment(callback: (android.net.Uri?) -> Unit) {
        pendingAttachmentCallback = callback
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        runCatching { startActivityForResult(intent, ATTACHMENT_REQUEST_CODE) }
            .onFailure {
                pendingAttachmentCallback = null
                callback(null)
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = if (resultCode == RESULT_OK) data?.data else null
        // 持久化读取授权，保证重启后附件缩略图仍可访问
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        when (requestCode) {
            CREATE_DOCUMENT_REQUEST_CODE -> {
                pendingCreateDocumentCallback?.invoke(uri)
                pendingCreateDocumentCallback = null
            }
            OPEN_DOCUMENT_REQUEST_CODE -> {
                pendingOpenDocumentCallback?.invoke(uri)
                pendingOpenDocumentCallback = null
            }
            PICK_IMAGE_REQUEST_CODE -> {
                pendingPickImageCallback?.invoke(uri)
                pendingPickImageCallback = null
            }
            ATTACHMENT_REQUEST_CODE -> {
                pendingAttachmentCallback?.invoke(uri)
                pendingAttachmentCallback = null
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            pendingPermissionCallback?.invoke(granted)
            pendingPermissionCallback = null
        }
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
            val firstLaunchDone by settingsRepository.firstLaunchDone
                .collectAsStateWithLifecycle(initialValue = true)

            MoneyBookTheme(themeMode = themeMode) {
                when {
                    // 设置尚未读取完成，先显示空白避免闪屏
                    lockEnabled == null -> Box(Modifier.fillMaxSize())
                    !firstLaunchDone -> OnboardingScreen(
                        onFinished = {
                            lifecycleScope.launch { settingsRepository.setFirstLaunchDone() }
                        }
                    )
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
