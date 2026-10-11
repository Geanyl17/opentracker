package io.github.geanyl17.opentracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.github.geanyl17.opentracker.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF2D5E4F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E8DE),
    onPrimaryContainer = Color(0xFF0B2B21),
    inversePrimary = Color(0xFF9ACFB8),
    secondary = Color(0xFF55615C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFE7E3),
    onSecondaryContainer = Color(0xFF131E1A),
    tertiary = Color(0xFF52606F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD7E3F3),
    onTertiaryContainer = Color(0xFF0F1D2A),
    background = Color(0xFFF8F8F6),
    onBackground = Color(0xFF1A1C1B),
    surface = Color(0xFFF8F8F6),
    onSurface = Color(0xFF1A1C1B),
    surfaceVariant = Color(0xFFE3E5E2),
    onSurfaceVariant = Color(0xFF5B605D),
    surfaceTint = Color(0xFF2D5E4F),
    inverseSurface = Color(0xFF2E3130),
    inverseOnSurface = Color(0xFFF0F1EF),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF8A8F8C),
    outlineVariant = Color(0xFFDADDD9),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF8F8F6),
    surfaceDim = Color(0xFFD9DAD7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3F0),
    surfaceContainer = Color(0xFFEDEEEB),
    surfaceContainerHigh = Color(0xFFE7E9E6),
    surfaceContainerHighest = Color(0xFFE1E3DF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ACFB8),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF1F4A3D),
    onPrimaryContainer = Color(0xFFC9EADB),
    inversePrimary = Color(0xFF2D5E4F),
    secondary = Color(0xFFB9C7C0),
    onSecondary = Color(0xFF24322D),
    secondaryContainer = Color(0xFF2D3934),
    onSecondaryContainer = Color(0xFFD5E3DC),
    tertiary = Color(0xFFB4C7DB),
    onTertiary = Color(0xFF1F3141),
    tertiaryContainer = Color(0xFF364858),
    onTertiaryContainer = Color(0xFFD3E4F7),
    background = Color(0xFF111312),
    onBackground = Color(0xFFE2E3E0),
    surface = Color(0xFF111312),
    onSurface = Color(0xFFE2E3E0),
    surfaceVariant = Color(0xFF3F4441),
    onSurfaceVariant = Color(0xFFBEC3BF),
    surfaceTint = Color(0xFF9ACFB8),
    inverseSurface = Color(0xFFE2E3E0),
    inverseOnSurface = Color(0xFF2E3130),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF888D8A),
    outlineVariant = Color(0xFF363A38),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF373A38),
    surfaceDim = Color(0xFF111312),
    surfaceContainerLowest = Color(0xFF0C0E0D),
    surfaceContainerLow = Color(0xFF191B1A),
    surfaceContainer = Color(0xFF1D201E),
    surfaceContainerHigh = Color(0xFF272A28),
    surfaceContainerHighest = Color(0xFF323533),
)

private val base = Typography()

private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Medium),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Medium),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
)

/** Digits of equal width, so amounts line up and don't jitter while they change. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

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
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
