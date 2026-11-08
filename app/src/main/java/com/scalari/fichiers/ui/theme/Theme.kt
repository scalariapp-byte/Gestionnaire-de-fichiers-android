package com.scalari.fichiers.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scalari.fichiers.R
import com.scalari.fichiers.data.AppSettings
import com.scalari.fichiers.data.ThemeMode

/** Inter : police d'interface (licence SIL OFL 1.1). */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

// Palette sobre : gris ardoise neutres, un seul bleu d'accent.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5BEA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4EAFF),
    onPrimaryContainer = Color(0xFF0B1F66),
    secondary = Color(0xFF475467),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEF1F6),
    onSecondaryContainer = Color(0xFF1D2939),
    tertiary = Color(0xFF0E7C86),
    tertiaryContainer = Color(0xFFD7F3F5),
    onTertiaryContainer = Color(0xFF053B40),
    error = Color(0xFFD92D20),
    background = Color(0xFFFCFCFD),
    onBackground = Color(0xFF101828),
    surface = Color(0xFFFCFCFD),
    onSurface = Color(0xFF101828),
    surfaceVariant = Color(0xFFF2F4F7),
    onSurfaceVariant = Color(0xFF667085),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9FB),
    surfaceContainer = Color(0xFFF2F4F7),
    surfaceContainerHigh = Color(0xFFEAECF0),
    surfaceContainerHighest = Color(0xFFE4E7EC),
    outline = Color(0xFFD0D5DD),
    outlineVariant = Color(0xFFEAECF0),
    inverseSurface = Color(0xFF1D2939),
    inverseOnSurface = Color(0xFFF2F4F7),
    inversePrimary = Color(0xFF9DB4FF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DA8FF),
    onPrimary = Color(0xFF0B1F66),
    primaryContainer = Color(0xFF233670),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF98A2B3),
    onSecondary = Color(0xFF101828),
    secondaryContainer = Color(0xFF232A37),
    onSecondaryContainer = Color(0xFFE4E7EC),
    tertiary = Color(0xFF5FD0D9),
    tertiaryContainer = Color(0xFF0B4A50),
    onTertiaryContainer = Color(0xFFD7F3F5),
    error = Color(0xFFF97066),
    background = Color(0xFF0C111D),
    onBackground = Color(0xFFF2F4F7),
    surface = Color(0xFF0C111D),
    onSurface = Color(0xFFF2F4F7),
    surfaceVariant = Color(0xFF1D2433),
    onSurfaceVariant = Color(0xFF98A2B3),
    surfaceContainerLowest = Color(0xFF080B14),
    surfaceContainerLow = Color(0xFF121826),
    surfaceContainer = Color(0xFF161C2A),
    surfaceContainerHigh = Color(0xFF1D2433),
    surfaceContainerHighest = Color(0xFF262E3F),
    outline = Color(0xFF344054),
    outlineVariant = Color(0xFF1F2737),
    inverseSurface = Color(0xFFF2F4F7),
    inverseOnSurface = Color(0xFF1D2939),
    inversePrimary = Color(0xFF2F5BEA),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

private fun TextStyle.inter(weight: FontWeight, spacing: Float? = null) =
    copy(fontFamily = Inter, fontWeight = weight, letterSpacing = spacing?.sp ?: letterSpacing)

private val AppTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.inter(FontWeight.SemiBold, -1f),
        displayMedium = t.displayMedium.inter(FontWeight.SemiBold, -0.8f),
        displaySmall = t.displaySmall.inter(FontWeight.SemiBold, -0.5f),
        headlineLarge = t.headlineLarge.inter(FontWeight.SemiBold, -0.6f),
        headlineMedium = t.headlineMedium.inter(FontWeight.SemiBold, -0.4f),
        headlineSmall = t.headlineSmall.inter(FontWeight.SemiBold, -0.3f),
        titleLarge = t.titleLarge.inter(FontWeight.SemiBold, -0.2f),
        titleMedium = t.titleMedium.inter(FontWeight.SemiBold, -0.1f),
        titleSmall = t.titleSmall.inter(FontWeight.SemiBold, 0f),
        bodyLarge = t.bodyLarge.inter(FontWeight.Normal, 0f),
        bodyMedium = t.bodyMedium.inter(FontWeight.Normal, 0f),
        bodySmall = t.bodySmall.inter(FontWeight.Normal, 0.1f),
        labelLarge = t.labelLarge.inter(FontWeight.Medium, 0f),
        labelMedium = t.labelMedium.inter(FontWeight.Medium, 0.2f),
        labelSmall = t.labelSmall.inter(FontWeight.Medium, 0.3f),
    )
}

/**
 * Variante noir pur : fonds à #000000 (pixels éteints sur écran OLED, économie de batterie),
 * surfaces surélevées en gris très sombres pour garder la hiérarchie visuelle.
 */
private fun ColorScheme.toPureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF1C1C1E),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0C),
    surfaceContainer = Color(0xFF111113),
    surfaceContainerHigh = Color(0xFF18181B),
    surfaceContainerHighest = Color(0xFF222226),
    surfaceVariant = Color(0xFF1A1A1D),
    outlineVariant = Color(0xFF1C1C20),
    inverseSurface = Color(0xFFE6E6EA),
)

@Composable
fun isAppInDarkTheme(settings: AppSettings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.BLACK -> true
}

@Composable
fun FichiersTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme(settings)
    val context = LocalContext.current
    val base = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val scheme = if (settings.themeMode == ThemeMode.BLACK) base.toPureBlack() else base
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, typography = AppTypography, content = content)
}
