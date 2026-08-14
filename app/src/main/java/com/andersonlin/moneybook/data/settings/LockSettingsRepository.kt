package com.andersonlin.moneybook.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom

/** 应用锁设置 */
data class LockSettings(
    val enabled: Boolean = false,
    val pinHash: String? = null,
    val salt: String? = null,
    val pinLength: Int = 0,
    val biometricEnabled: Boolean = false
) {
    val hasPin: Boolean get() = pinHash != null && salt != null && pinLength > 0
}

private val Context.lockDataStore by preferencesDataStore(name = "lock_settings")

/** 应用锁仓库：PIN 以「盐 + SHA-256」哈希存储，不明文保存 */
class LockSettingsRepository(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("lock_enabled")
    private val pinHashKey = stringPreferencesKey("pin_hash")
    private val saltKey = stringPreferencesKey("pin_salt")
    private val pinLengthKey = intPreferencesKey("pin_length")
    private val biometricKey = booleanPreferencesKey("biometric_enabled")

    val settings: Flow<LockSettings> = context.lockDataStore.data.map { prefs ->
        LockSettings(
            enabled = prefs[enabledKey] ?: false,
            pinHash = prefs[pinHashKey],
            salt = prefs[saltKey],
            pinLength = prefs[pinLengthKey] ?: 0,
            biometricEnabled = prefs[biometricKey] ?: false
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.lockDataStore.edit { it[enabledKey] = enabled }
    }

    /** 设置新 PIN（4-6 位数字），生成随机盐并存储哈希 */
    suspend fun setPin(pin: String) {
        val saltBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt = saltBytes.joinToString("") { "%02x".format(it) }
        context.lockDataStore.edit {
            it[pinHashKey] = hashPin(pin, salt)
            it[saltKey] = salt
            it[pinLengthKey] = pin.length
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.lockDataStore.edit { it[biometricKey] = enabled }
    }

    fun verifyPin(pin: String, settings: LockSettings): Boolean {
        val salt = settings.salt ?: return false
        return settings.pinHash == hashPin(pin, salt)
    }

    private fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((pin + salt).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
