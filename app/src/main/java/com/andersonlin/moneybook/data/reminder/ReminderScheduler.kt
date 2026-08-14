package com.andersonlin.moneybook.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.andersonlin.moneybook.MainActivity
import com.andersonlin.moneybook.MoneyBookApp
import com.andersonlin.moneybook.R
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** 每日记账提醒：本地通知（WorkManager 定时，无任何网络行为） */
object ReminderScheduler {

    private const val WORK_NAME = "daily_reminder"

    /** 按设定时间调度每日提醒（重复调用会更新，不重复排队） */
    fun schedule(context: Context, hour: Int, minute: Int) {
        val now = LocalTime.now()
        val target = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        val today = Duration.between(now, target).toMillis()
        val delayMillis = if (today > 0) today else today + 24 * 3600 * 1000L
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

/** 提醒任务：读取设置并发通知 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MoneyBookApp
        val settings = app.reminderRepository.settings.first()
        if (!settings.enabled) return Result.success()
        NotificationHelper.showReminder(applicationContext)
        return Result.success()
    }
}

object NotificationHelper {

    private const val CHANNEL_ID = "reminder_channel"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "记账提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "每日记账提醒" }
            manager.createNotificationChannel(channel)
        }
    }

    fun showReminder(context: Context) {
        // Android 13+ 无通知权限时静默跳过
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("记账本")
            .setContentText("今天记账了吗？记得记一笔哦 📝")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(1, notification) }
    }
}
