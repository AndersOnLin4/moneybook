package com.andersonlin.moneybook.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 主题模式：跟随系统 / 浅色 / 深色 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.dataStore by preferencesDataStore(name = "settings")

/** 设置仓库：DataStore 持久化主题模式 */
class SettingsRepository(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[themeModeKey] ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeModeKey] = mode.name }
    }
}
