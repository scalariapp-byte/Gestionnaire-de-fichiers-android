package com.scalari.fichiers.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.scalari.fichiers.data.AppSettings
import com.scalari.fichiers.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B5EBF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF565E71),
    secondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFF6A4FD8),
    tertiaryContainer = Color(0xFFE6DEFF),
    background = Color(0xFFF8F9FF),
    surface = Color(0xFFF8F9FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F3FC),
    surfaceContainer = Color(0xFFEBEEF8),
    surfaceContainerHigh = Color(0xFFE5E8F3),
    surfaceContainerHighest = Color(0xFFDFE2ED),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBEC6DC),
    secondaryContainer = Color(0xFF3E4759),
    tertiary = Color(0xFFCABEFF),
    tertiaryContainer = Color(0xFF4A35B0),
    background = Color(0xFF0F1218),
    surface = Color(0xFF0F1218),
    surfaceContainerLowest = Color(0xFF0A0D12),
    surfaceContainerLow = Color(0xFF171A21),
    surfaceContainer = Color(0xFF1B1F26),
    surfaceContainerHigh = Color(0xFF252931),
    surfaceContainerHighest = Color(0xFF30343C),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun isAppInDarkTheme(settings: AppSettings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun FichiersTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme(settings)
    val context = LocalContext.current
    val scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, typography = AppTypography, content = content)
}
