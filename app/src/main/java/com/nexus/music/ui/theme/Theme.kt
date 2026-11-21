package com.nexus.music.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val HyperLight = lightColorScheme(
    primary = Color(0xFF2878F0), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEAFF), onPrimaryContainer = Color(0xFF082B5D),
    secondary = Color(0xFF52677F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0EAF6), onSecondaryContainer = Color(0xFF182B40),
    tertiary = Color(0xFF6E63C7), onTertiary = Color.White,
    background = Color(0xFFF4F6FA), surface = Color(0xFFF8F9FC),
    surfaceVariant = Color(0xFFE8EDF5), onSurface = Color(0xFF161B22),
    onSurfaceVariant = Color(0xFF5C6673), outline = Color(0xFF8793A2)
)
private val HyperDark = darkColorScheme(
    primary = Color(0xFF8AB8FF), onPrimary = Color(0xFF003064),
    primaryContainer = Color(0xFF164780), onPrimaryContainer = Color(0xFFD7E7FF),
    secondary = Color(0xFFB5C9E2), onSecondary = Color(0xFF203246),
    secondaryContainer = Color(0xFF354B63), onSecondaryContainer = Color(0xFFD9E7F7),
    tertiary = Color(0xFFC5B9FF), onTertiary = Color(0xFF30266B),
    background = Color(0xFF10151C), surface = Color(0xFF171E27),
    surfaceVariant = Color(0xFF27313D), onSurface = Color(0xFFE5ECF5),
    onSurfaceVariant = Color(0xFFB8C3D0), outline = Color(0xFF7D8997)
)

private val LaneLight = lightColorScheme(
    primary = Color(0xFF262626), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E5E5), onPrimaryContainer = Color(0xFF171717),
    secondary = Color(0xFF626262), onSecondary = Color.White,
    background = Color(0xFFF7F7F7), surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDEDED), onSurface = Color(0xFF171717),
    onSurfaceVariant = Color(0xFF666666), outline = Color(0xFF999999)
)
private val LaneDark = darkColorScheme(
    primary = Color(0xFFE7E7E7), onPrimary = Color(0xFF151515),
    primaryContainer = Color(0xFF383838), onPrimaryContainer = Color(0xFFF2F2F2),
    secondary = Color(0xFFB9B9B9), onSecondary = Color(0xFF242424),
    background = Color(0xFF0B0B0B), surface = Color(0xFF141414),
    surfaceVariant = Color(0xFF252525), onSurface = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFFB7B7B7), outline = Color(0xFF777777)
)

private val LiquidLight = lightColorScheme(
    primary = Color(0xFF007F83), onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F1EF), onPrimaryContainer = Color(0xFF002F31),
    secondary = Color(0xFF526B8A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E5FF), onSecondaryContainer = Color(0xFF142C46),
    tertiary = Color(0xFF7954A3), onTertiary = Color.White,
    background = Color(0xFFEFF7F7), surface = Color(0xFFF7FCFC),
    surfaceVariant = Color(0xFFDDECEC), onSurface = Color(0xFF102526),
    onSurfaceVariant = Color(0xFF526B6C), outline = Color(0xFF789394)
)
private val LiquidDark = darkColorScheme(
    primary = Color(0xFF7AD9D5), onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF005454), onPrimaryContainer = Color(0xFFB8F1EF),
    secondary = Color(0xFFB7CBEA), onSecondary = Color(0xFF233750),
    secondaryContainer = Color(0xFF3A526E), onSecondaryContainer = Color(0xFFD9E8FF),
    tertiary = Color(0xFFD5B8FA), onTertiary = Color(0xFF3B235B),
    background = Color(0xFF0D1B1D), surface = Color(0xFF142527),
    surfaceVariant = Color(0xFF263B3D), onSurface = Color(0xFFE2F2F1),
    onSurfaceVariant = Color(0xFFB3CCCA), outline = Color(0xFF789493)
)

@Composable
fun NexusTheme(themeMode: String, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val context = LocalContext.current
    val colors = when (themeMode) {
        "lane" -> if (dark) LaneDark else LaneLight
        "liquid" -> if (dark) LiquidDark else LiquidLight
        "material3" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) darkColorScheme() else lightColorScheme()
        "hyperos", "system", "light", "dark" -> if (dark) HyperDark else HyperLight
        else -> if (dark) HyperDark else HyperLight
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colors.surface.toArgb()
            window.navigationBarColor = colors.surface.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(colorScheme = colors, content = content)
}
