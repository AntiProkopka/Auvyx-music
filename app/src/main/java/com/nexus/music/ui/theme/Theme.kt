package com.nexus.music.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import android.os.Build
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AUVYXLight = lightColorScheme(
    primary = Color(0xFF4059C9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2FF),
    onPrimaryContainer = Color(0xFF101C62),
    secondary = Color(0xFF536078),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E6F5),
    onSecondaryContainer = Color(0xFF17243B),
    tertiary = Color(0xFF007D78),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF9CF2E9),
    onTertiaryContainer = Color(0xFF00201E),
    background = Color(0xFFF5F6FB),
    onBackground = Color(0xFF171A24),
    surface = Color(0xFFF8F8FC),
    onSurface = Color(0xFF171A24),
    surfaceVariant = Color(0xFFE4E7F0),
    onSurfaceVariant = Color(0xFF454B5C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F2F8),
    surfaceContainer = Color(0xFFEAEDF5),
    surfaceContainerHigh = Color(0xFFE3E7F1),
    surfaceContainerHighest = Color(0xFFDDE1EC),
    outline = Color(0xFF747B8D),
    outlineVariant = Color(0xFFC4C9D7)
)

private val AUVYXDark = darkColorScheme(
    primary = Color(0xFFB5C2FF),
    onPrimary = Color(0xFF17266F),
    primaryContainer = Color(0xFF2D4198),
    onPrimaryContainer = Color(0xFFE0E5FF),
    secondary = Color(0xFFBBC6DF),
    onSecondary = Color(0xFF263247),
    secondaryContainer = Color(0xFF3D4962),
    onSecondaryContainer = Color(0xFFE0E7FA),
    tertiary = Color(0xFF80D8CF),
    onTertiary = Color(0xFF003733),
    tertiaryContainer = Color(0xFF00504A),
    onTertiaryContainer = Color(0xFF9CF2E9),
    background = Color(0xFF10131B),
    onBackground = Color(0xFFE5E8F2),
    surface = Color(0xFF131722),
    onSurface = Color(0xFFE5E8F2),
    surfaceVariant = Color(0xFF41485A),
    onSurfaceVariant = Color(0xFFC1C8DA),
    surfaceContainerLowest = Color(0xFF0C0F16),
    surfaceContainerLow = Color(0xFF191E2A),
    surfaceContainer = Color(0xFF1D2331),
    surfaceContainerHigh = Color(0xFF252C3C),
    surfaceContainerHighest = Color(0xFF30384A),
    outline = Color(0xFF8B94A8),
    outlineVariant = Color(0xFF41485A)
)

private val AUVYXShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

private val expressiveTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun NexusTheme(themeMode: String, style: String, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val base = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) AUVYXDark else AUVYXLight
    val colors = when (style) {
        "hyper" -> base.copy(surfaceContainer = base.surface.copy(alpha = .83f))
        "glass" -> base.copy(surfaceContainer = base.surface.copy(alpha = .62f),
            surfaceContainerLow = base.surfaceVariant.copy(alpha = .70f))
        "lane" -> base.copy(
            primary = if (dark) Color(0xFFC4E96D) else Color(0xFF486500),
            onPrimary = if (dark) Color(0xFF253600) else Color.White
        )
        else -> base
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

    androidx.compose.runtime.CompositionLocalProvider(LocalStyle provides style) {
        MaterialExpressiveTheme(
            colorScheme = colors,
            typography = expressiveTypography,
            motionScheme = MotionScheme.expressive(),
            shapes = when (style) {
                "lane" -> Shapes(small = RoundedCornerShape(3.dp), medium = RoundedCornerShape(4.dp), large = RoundedCornerShape(6.dp))
                "glass" -> Shapes(medium = RoundedCornerShape(30.dp), large = RoundedCornerShape(40.dp))
                else -> AUVYXShapes
            },
            content = { CompositionLocalProvider(LocalContentColor provides colors.onBackground) { content() } }
        )
    }
}

val LocalStyle = androidx.compose.runtime.staticCompositionLocalOf { "material" }
