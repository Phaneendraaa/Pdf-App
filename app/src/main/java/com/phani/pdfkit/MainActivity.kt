package com.phani.pdfkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.isSystemInDarkTheme
import com.phani.pdfkit.data.Settings
import com.phani.pdfkit.ui.PdfKitApp
import com.phani.pdfkit.ui.theme.PdfKitTheme
import com.phani.pdfkit.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by Graph.prefs.settings.collectAsState(initial = Settings())
            val mode = when (settings.theme) { "Dark" -> ThemeMode.Dark; "System" -> ThemeMode.System; else -> ThemeMode.Light }
            val dark = mode == ThemeMode.Dark || (mode == ThemeMode.System && isSystemInDarkTheme())
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
            }
            PdfKitTheme(mode) { PdfKitApp(settings) }
        }
    }
}
