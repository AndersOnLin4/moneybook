package com.andersonlin.moneybook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andersonlin.moneybook.data.settings.ThemeMode
import com.andersonlin.moneybook.ui.navigation.AppRoot
import com.andersonlin.moneybook.ui.theme.MoneyBookTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsRepository = (application as MoneyBookApp).settingsRepository
        setContent {
            val themeMode by settingsRepository.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            MoneyBookTheme(themeMode = themeMode) {
                AppRoot()
            }
        }
    }
}
