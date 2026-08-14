package com.andersonlin.moneybook.data.reminder

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 记账提醒设置 */
data class ReminderSettings(
    val enabled: Boolean = false,
    val hour: Int = 20,
    val minute: Int = 0
)

private val Context.reminderDataStore by preferencesDataStore(name = "reminder_settings")

/** 记账提醒仓库（DataStore 持久化） */
class ReminderRepository(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("reminder_enabled")
    private val hourKey = intPreferencesKey("reminder_hour")
    private val minuteKey = intPreferencesKey("reminder_minute")

    val settings: Flow<ReminderSettings> = context.reminderDataStore.data.map { prefs ->
        ReminderSettings(
            enabled = prefs[enabledKey] ?: false,
            hour = prefs[hourKey] ?: 20,
            minute = prefs[minuteKey] ?: 0
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.reminderDataStore.edit { it[enabledKey] = enabled }
    }

    suspend fun setTime(hour: Int, minute: Int) {
        context.reminderDataStore.edit {
            it[hourKey] = hour
            it[minuteKey] = minute
        }
    }
}
