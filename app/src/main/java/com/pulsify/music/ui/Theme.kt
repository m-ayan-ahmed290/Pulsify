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
import com.pulsify.music.data.Settings

val Violet = Color(0xFFC5ABFF)
private val Dark = darkColorScheme(primary = Violet, onPrimary = Color(0xFF291454), primaryContainer = Color(0xFF39265D),
    secondary = Color(0xFFE0C7FF), background = Color(0xFF101116), surface = Color(0xFF101116),
    surfaceContainer = Color(0xFF1C1D25), surfaceVariant = Color(0xFF292A35), onSurface = Color(0xFFF5F1FA),
    onSurfaceVariant = Color(0xFFAAA7B6), outline = Color(0xFF494652))
private val Light = lightColorScheme(primary = Color(0xFF6842AF), primaryContainer = Color(0xFFEBDDFF),
    background = Color(0xFFFAF8FF), surface = Color(0xFFFAF8FF), surfaceContainer = Color(0xFFF0EAF7))
@Composable fun PulsifyTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = settings.theme == "Dark" || (settings.theme == "System" && isSystemInDarkTheme())
    val colors = if(settings.dynamic && Build.VERSION.SDK_INT >= 31) {
        if(dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if(dark) Dark else Light
    val view = LocalView.current
    if(!view.isInEditMode) SideEffect {
        (view.context as? android.app.Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
