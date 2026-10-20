package com.pulsify.music.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pulsify.music.data.Settings

val Violet = Color(0xFFC1A3FF)
val Acid = Color(0xFFD4F883)
val Peach = Color(0xFFFFB6A0)
private val Dark = darkColorScheme(primary = Violet, onPrimary = Color(0xFF201235), primaryContainer = Color(0xFF342547),
    secondary = Acid, onSecondary = Color(0xFF18230B), secondaryContainer = Color(0xFF29361C),
    background = Color(0xFF0B0C0F), surface = Color(0xFF0B0C0F), surfaceContainer = Color(0xFF191B20),
    surfaceVariant = Color(0xFF25272E), onSurface = Color(0xFFF6F5F8), onSurfaceVariant = Color(0xFFA2A4B0), outline = Color(0xFF393B45))
private val Light = lightColorScheme(primary = Color(0xFF7344BA), primaryContainer = Color(0xFFECDDFF),
    secondary = Color(0xFF3E620F), onSecondary = Color.White, secondaryContainer = Color(0xFFD4F1B5),
    background = Color(0xFFFAF9FC), surface = Color(0xFFFAF9FC), surfaceContainer = Color(0xFFF0EDF5), surfaceVariant = Color(0xFFE8E4EE))
private val Type = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 39.sp, letterSpacing = (-1.2).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 33.sp, letterSpacing = (-.7).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 29.sp, letterSpacing = (-.5).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-.4).sp))
@Composable fun PulsifyTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = settings.theme == "Dark" || (settings.theme == "System" && isSystemInDarkTheme())
    val colors = if(settings.dynamic && Build.VERSION.SDK_INT >= 31) {
        if(dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if(dark) Dark else Light
    val view = LocalView.current
    if(!view.isInEditMode) SideEffect {
        (view.context as? android.app.Activity)?.window?.let { window -> WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
        } }
    }
    MaterialTheme(colorScheme = colors, typography = Type, content = content)
}
