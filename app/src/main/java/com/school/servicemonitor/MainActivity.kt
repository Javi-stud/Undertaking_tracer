package com.school.servicemonitor

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.school.servicemonitor.data.repository.SettingsRepository
import com.school.servicemonitor.ui.components.AppBackground
import com.school.servicemonitor.ui.navigation.AppNavigation
import com.school.servicemonitor.ui.theme.AppThemeColor
import com.school.servicemonitor.ui.theme.ServiceMonitorTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        val settings = (application as App).container.settingsRepository
        setContent {
            val map by settings.observeAll().collectAsState(initial = emptyMap())
            val theme = AppThemeColor.fromKey(map[SettingsRepository.KEY_THEME_COLOR])
            ServiceMonitorTheme(themeColor = theme) {
                AppBackground(accent = theme.accent) { AppNavigation() }
            }
        }
    }
}