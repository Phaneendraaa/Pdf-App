package com.phani.pdfkit

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.phani.pdfkit.data.Settings
import com.phani.pdfkit.ui.PdfKitApp
import com.phani.pdfkit.ui.components.PdfGlyph
import com.phani.pdfkit.ui.components.PrimaryButton
import com.phani.pdfkit.ui.theme.Pk
import com.phani.pdfkit.ui.theme.PdfKitTheme
import com.phani.pdfkit.ui.theme.ThemeMode
import com.phani.pdfkit.util.AppLock
import com.phani.pdfkit.util.IncomingIntents

class MainActivity : FragmentActivity() {
    private var locked by mutableStateOf(true)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) IncomingIntents.handle(intent)
        setContent {
            val loaded by Graph.prefs.settings.collectAsState(initial = null as Settings?)
            val settings = loaded ?: Settings()
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
            val needLock = settings.appLock && locked && AppLock.available(this)
            LaunchedEffect(loaded != null, needLock) {
                if (loaded != null && !needLock) locked = false
                if (needLock) AppLock.prompt(this@MainActivity) { locked = false }
            }
            PdfKitTheme(mode) {
                when {
                    loaded == null -> Box(Modifier.fillMaxSize().background(Pk.colors.bg))
                    needLock -> LockScreen { AppLock.prompt(this@MainActivity) { locked = false } }
                    else -> PdfKitApp(settings)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        IncomingIntents.handle(intent)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Lock again after 30 s in the background.
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > 30_000) locked = true
    }
}

@androidx.compose.runtime.Composable
private fun LockScreen(onUnlock: () -> Unit) {
    val c = Pk.colors
    Column(
        Modifier.fillMaxSize().background(c.bg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        PdfGlyph(width = 56.dp)
        androidx.compose.material3.Text("Pdf Kit is locked", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.padding(top = 20.dp))
        androidx.compose.material3.Text("Use your fingerprint, face or screen lock to open it.", fontSize = 15.sp, color = c.ink2, modifier = Modifier.padding(top = 6.dp, bottom = 28.dp))
        PrimaryButton("Unlock", onClick = onUnlock)
    }
}
