package com.phani.pdfkit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Pdf Kit palette. Read colors through [Pk.colors], never hard-code them in screens. */
@Immutable
data class PkColors(
    val bg: Color,
    val card: Color,
    val surf: Color,
    val line: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val blue: Color,
    val blueDark: Color,
    val blueTint: Color,
    val red: Color,
    val redTint: Color,
    val green: Color,
    val greenTint: Color,
    val amber: Color,
    val scrim: Color,
    val isDark: Boolean,
)

val LightPk = PkColors(
    bg = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    surf = Color(0xFFF4F5F8),
    line = Color(0xFFECEEF2),
    ink = Color(0xFF101217),
    ink2 = Color(0xFF5B606B),
    ink3 = Color(0xFF9AA0AA),
    blue = Color(0xFF2463EB),
    blueDark = Color(0xFF1B50D6),
    blueTint = Color(0xFFEEF3FF),
    red = Color(0xFFEF3B3B),
    redTint = Color(0xFFFFEEEE),
    green = Color(0xFF16A34A),
    greenTint = Color(0xFFEDFAF2),
    amber = Color(0xFFF5B301),
    scrim = Color(0x52101217),
    isDark = false,
)

val DarkPk = PkColors(
    bg = Color(0xFF0E1014),
    card = Color(0xFF171A20),
    surf = Color(0xFF1C2028),
    line = Color(0xFF262A33),
    ink = Color(0xFFF1F3F7),
    ink2 = Color(0xFFA5ABB6),
    ink3 = Color(0xFF6E7480),
    blue = Color(0xFF5B8CFF),
    blueDark = Color(0xFF4677F0),
    blueTint = Color(0xFF1B2540),
    red = Color(0xFFF25555),
    redTint = Color(0xFF3A1E22),
    green = Color(0xFF34C26A),
    greenTint = Color(0xFF16291E),
    amber = Color(0xFFF5B301),
    scrim = Color(0x99000000),
    isDark = true,
)

/** Tool category tints (icon color, tile background) for light and dark. */
@Immutable
data class Tint(val fg: Color, val bg: Color)

fun tint(fg: Long, bgLight: Long, dark: Boolean): Tint =
    if (dark) Tint(Color(fg), Color(fg).copy(alpha = 0.16f)) else Tint(Color(fg), Color(bgLight))

val LocalPk = staticCompositionLocalOf { LightPk }

object Pk {
    val colors: PkColors @Composable get() = LocalPk.current
}

private val Sans = FontFamily.SansSerif

val PkType = Typography(
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = (-0.2).sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.5.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp),
)

enum class ThemeMode { Light, Dark, System }

@Composable
fun PdfKitTheme(mode: ThemeMode = ThemeMode.Light, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val pk = if (dark) DarkPk else LightPk
    val scheme = if (dark) {
        darkColorScheme(
            primary = pk.blue, onPrimary = Color.White, background = pk.bg, surface = pk.card,
            onBackground = pk.ink, onSurface = pk.ink, surfaceVariant = pk.surf, outline = pk.line,
            surfaceContainerLow = pk.card, surfaceContainer = pk.card, surfaceContainerHigh = pk.card,
        )
    } else {
        lightColorScheme(
            primary = pk.blue, onPrimary = Color.White, background = pk.bg, surface = pk.card,
            onBackground = pk.ink, onSurface = pk.ink, surfaceVariant = pk.surf, outline = pk.line,
            surfaceContainerLow = pk.card, surfaceContainer = pk.card, surfaceContainerHigh = pk.card,
        )
    }
    CompositionLocalProvider(LocalPk provides pk) {
        MaterialTheme(colorScheme = scheme, typography = PkType, content = content)
    }
}
