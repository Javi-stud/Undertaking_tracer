package com.school.servicemonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.school.servicemonitor.data.repository.SettingsRepository
import com.school.servicemonitor.ui.navigation.AppNavigation
import com.school.servicemonitor.ui.theme.AppThemeColor
import com.school.servicemonitor.ui.theme.ServiceMonitorTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as App).container
        setContent {
            val settingsMap by container.settingsRepository.observeAll()
                .collectAsStateWithLifecycle(initialValue = emptyMap())

            val themeKey = settingsMap[SettingsRepository.KEY_THEME_COLOR]
            val appThemeColor = AppThemeColor.fromKey(themeKey)

            ServiceMonitorTheme(themeColor = appThemeColor) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}
