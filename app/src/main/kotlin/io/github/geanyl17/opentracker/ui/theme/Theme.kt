package io.github.geanyl17.opentracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.github.geanyl17.opentracker.R
import io.github.geanyl17.opentracker.data.ThemeMode

/** Colors Material doesn't have a slot for: cards, the dark summary card and money going in or out. */
@Immutable
data class AppColors(
    val card: Color,
    val hero: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val heroTrack: Color,
    val accent: Color,
    val onAccent: Color,
    val positive: Color,
    val negative: Color,
    val dark: Boolean,
)

private val LightApp = AppColors(
    card = Color(0xFFFFFFFF),
    hero = Color(0xFF14161B),
    onHero = Color(0xFFFFFFFF),
    onHeroMuted = Color(0xFF9CA3AF),
    heroTrack = Color(0xFF2B2F38),
    accent = Color(0xFF10B981),
    onAccent = Color(0xFF04140D),
    positive = Color(0xFF059669),
    negative = Color(0xFFE5484D),
    dark = false,
)

private val DarkApp = AppColors(
    card = Color(0xFF17191E),
    hero = Color(0xFF1F2229),
    onHero = Color(0xFFF5F6F8),
    onHeroMuted = Color(0xFF9AA1AD),
    heroTrack = Color(0xFF343944),
    accent = Color(0xFF34D399),
    onAccent = Color(0xFF04140D),
    positive = Color(0xFF3DD68C),
    negative = Color(0xFFFF6369),
    dark = true,
)

val LocalAppColors = staticCompositionLocalOf { LightApp }

private val LightColors = lightColorScheme(
    primary = Color(0xFF14161B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6E8EC),
    onPrimaryContainer = Color(0xFF14161B),
    inversePrimary = Color(0xFFF5F6F8),
    secondary = Color(0xFF4B5563),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF14161B),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF059669),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),
    background = Color(0xFFF2F3F5),
    onBackground = Color(0xFF14161B),
    surface = Color(0xFFF2F3F5),
    onSurface = Color(0xFF14161B),
    surfaceVariant = Color(0xFFE6E8EC),
    onSurfaceVariant = Color(0xFF6B7280),
    surfaceTint = Color(0xFF14161B),
    inverseSurface = Color(0xFF14161B),
    inverseOnSurface = Color(0xFFF5F6F8),
    error = Color(0xFFDC3B40),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE4E4),
    onErrorContainer = Color(0xFF5C0F12),
    outline = Color(0xFFC6CAD2),
    outlineVariant = Color(0xFFE3E5E9),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDCDEE2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9FA),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE6E8EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF5F6F8),
    onPrimary = Color(0xFF0B0C0F),
    primaryContainer = Color(0xFF262A31),
    onPrimaryContainer = Color(0xFFF5F6F8),
    inversePrimary = Color(0xFF14161B),
    secondary = Color(0xFFB4BAC4),
    onSecondary = Color(0xFF0B0C0F),
    secondaryContainer = Color(0xFFF5F6F8),
    onSecondaryContainer = Color(0xFF0B0C0F),
    tertiary = Color(0xFF3DD68C),
    onTertiary = Color(0xFF04140D),
    tertiaryContainer = Color(0xFF0E3B2A),
    onTertiaryContainer = Color(0xFFB7F5D6),
    background = Color(0xFF0B0C0F),
    onBackground = Color(0xFFF5F6F8),
    surface = Color(0xFF0B0C0F),
    onSurface = Color(0xFFF5F6F8),
    surfaceVariant = Color(0xFF262A31),
    onSurfaceVariant = Color(0xFF9AA1AD),
    surfaceTint = Color(0xFFF5F6F8),
    inverseSurface = Color(0xFFF5F6F8),
    inverseOnSurface = Color(0xFF14161B),
    error = Color(0xFFFF6369),
    onError = Color(0xFF3B0709),
    errorContainer = Color(0xFF5C1417),
    onErrorContainer = Color(0xFFFFDADA),
    outline = Color(0xFF3F444E),
    outlineVariant = Color(0xFF262A31),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A2E36),
    surfaceDim = Color(0xFF0B0C0F),
    surfaceContainerLowest = Color(0xFF08090B),
    surfaceContainerLow = Color(0xFF121418),
    surfaceContainer = Color(0xFF17191E),
    surfaceContainerHigh = Color(0xFF1C1F25),
    surfaceContainerHighest = Color(0xFF262A31),
)

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: FontWeight) =
    Font(R.font.plus_jakarta_sans, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val Jakarta = FontFamily(
    jakarta(FontWeight.Normal),
    jakarta(FontWeight.Medium),
    jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold),
    jakarta(FontWeight.ExtraBold),
)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp),
        displayMedium = displayMedium.copy(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-1.2).sp),
        displaySmall = displaySmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
        headlineLarge = headlineLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
        headlineMedium = headlineMedium.copy(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = titleLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
        titleSmall = titleSmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
        bodyLarge = bodyLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        bodyMedium = bodyMedium.copy(fontFamily = Jakarta, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        bodySmall = bodySmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        labelLarge = labelLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        labelMedium = labelMedium.copy(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelSmall = labelSmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Digits of equal width, so amounts line up and don't jitter while they change. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}

@Composable
fun OpenTrackerTheme(mode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalAppColors provides if (dark) DarkApp else LightApp) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
