package com.anywall.converter.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.anywall.converter.core.AppAccentPalette
import com.anywall.converter.core.AppThemeMode

private data class AccentSchemeTokens(
    val primaryDark: Color,
    val onPrimaryDark: Color,
    val containerDark: Color,
    val onContainerDark: Color,
    val primaryLight: Color,
    val onPrimaryLight: Color,
    val containerLight: Color,
    val onContainerLight: Color
)

private fun resolveAccentTokens(palette: AppAccentPalette): AccentSchemeTokens {
    return when (palette) {
        AppAccentPalette.INDIGO_STUDIO -> AccentSchemeTokens(
            primaryDark = Color(0xFF818CF8),
            onPrimaryDark = Color(0xFF1E1B4B),
            containerDark = Color(0xFF312E81),
            onContainerDark = Color(0xFFE0E7FF),
            primaryLight = Color(0xFF4F46E5),
            onPrimaryLight = Color(0xFFFFFFFF),
            containerLight = Color(0xFFE0E7FF),
            onContainerLight = Color(0xFF1E1B4B)
        )
        AppAccentPalette.EMERALD -> AccentSchemeTokens(
            primaryDark = Color(0xFF34D399),
            onPrimaryDark = Color(0xFF022C22),
            containerDark = Color(0xFF065F46),
            onContainerDark = Color(0xFFD1FAE5),
            primaryLight = Color(0xFF059669),
            onPrimaryLight = Color(0xFFFFFFFF),
            containerLight = Color(0xFFD1FAE5),
            onContainerLight = Color(0xFF064E3B)
        )
        AppAccentPalette.AMBER -> AccentSchemeTokens(
            primaryDark = Color(0xFFFBBF24),
            onPrimaryDark = Color(0xFF451A03),
            containerDark = Color(0xFF78350F),
            onContainerDark = Color(0xFFFEF3C7),
            primaryLight = Color(0xFFD97706),
            onPrimaryLight = Color(0xFFFFFFFF),
            containerLight = Color(0xFFFEF3C7),
            onContainerLight = Color(0xFF451A03)
        )
        AppAccentPalette.ROSE -> AccentSchemeTokens(
            primaryDark = Color(0xFFFB7185),
            onPrimaryDark = Color(0xFF4C0519),
            containerDark = Color(0xFF881337),
            onContainerDark = Color(0xFFFFE4E6),
            primaryLight = Color(0xFFE11D48),
            onPrimaryLight = Color(0xFFFFFFFF),
            containerLight = Color(0xFFFFE4E6),
            onContainerLight = Color(0xFF4C0519)
        )
        AppAccentPalette.CYAN -> AccentSchemeTokens(
            primaryDark = Color(0xFF22D3EE),
            onPrimaryDark = Color(0xFF083344),
            containerDark = Color(0xFF155E75),
            onContainerDark = Color(0xFFCFFAFE),
            primaryLight = Color(0xFF0891B2),
            onPrimaryLight = Color(0xFFFFFFFF),
            containerLight = Color(0xFFCFFAFE),
            onContainerLight = Color(0xFF083344)
        )
        AppAccentPalette.MONOCHROME -> AccentSchemeTokens(
            primaryDark = Color(0xFFE2E8F0),
            onPrimaryDark = Color(0xFF0F172A),
            containerDark = Color(0xFF334155),
            onContainerDark = Color(0xFFF8FAFC),
            primaryLight = Color(0xFF334155),
            onPrimaryLight = Color(0xFFFFFFFF),
            containerLight = Color(0xFFE2E8F0),
            onContainerLight = Color(0xFF0F172A)
        )
    }
}

private fun buildCustomColorScheme(
    isDark: Boolean,
    isAmoledBlack: Boolean,
    palette: AppAccentPalette
): ColorScheme {
    val tokens = resolveAccentTokens(palette)
    return if (isDark) {
        darkColorScheme(
            primary = tokens.primaryDark,
            onPrimary = tokens.onPrimaryDark,
            primaryContainer = tokens.containerDark,
            onPrimaryContainer = tokens.onContainerDark,
            secondary = tokens.primaryDark,
            onSecondary = tokens.onPrimaryDark,
            secondaryContainer = tokens.containerDark,
            onSecondaryContainer = tokens.onContainerDark,
            tertiary = AnyWallEmeraldTertiaryDark,
            onTertiary = AnyWallEmeraldOnTertiaryDark,
            tertiaryContainer = AnyWallEmeraldContainerDark,
            onTertiaryContainer = AnyWallEmeraldOnContainerDark,
            background = if (isAmoledBlack) AnyWallBackgroundAmoled else AnyWallBackgroundDark,
            surface = if (isAmoledBlack) AnyWallSurfaceAmoled else AnyWallSurfaceDark,
            surfaceVariant = if (isAmoledBlack) AnyWallSurfaceVariantAmoled else AnyWallSurfaceVariantDark
        )
    } else {
        lightColorScheme(
            primary = tokens.primaryLight,
            onPrimary = tokens.onPrimaryLight,
            primaryContainer = tokens.containerLight,
            onPrimaryContainer = tokens.onContainerLight,
            secondary = tokens.primaryLight,
            onSecondary = tokens.onPrimaryLight,
            secondaryContainer = tokens.containerLight,
            onSecondaryContainer = tokens.onContainerLight,
            tertiary = AnyWallEmeraldTertiaryLight,
            onTertiary = AnyWallEmeraldOnTertiaryLight,
            tertiaryContainer = AnyWallEmeraldContainerLight,
            onTertiaryContainer = AnyWallEmeraldOnContainerLight,
            background = AnyWallBackgroundLight,
            surface = AnyWallSurfaceLight,
            surfaceVariant = AnyWallSurfaceVariantLight
        )
    }
}

/**
 * Tema Material 3 de AnyWall (v0.4) con soporte para:
 * - `SYSTEM`, `LIGHT`, `DARK`, y `AMOLED_BLACK` (`#000000`).
 * - Material You (`dynamicColor`) en Android 12+.
 * - 6 paletas de acento (`INDIGO_STUDIO`, `EMERALD`, `AMBER`, `ROSE`, `CYAN`, `MONOCHROME`).
 */
@Composable
fun AnyWallTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    darkTheme: Boolean = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED_BLACK -> true
    },
    dynamicColor: Boolean = false,
    accentPalette: AppAccentPalette = AppAccentPalette.INDIGO_STUDIO,
    content: @Composable () -> Unit
) {
    val isAmoled = themeMode == AppThemeMode.AMOLED_BLACK
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> buildCustomColorScheme(
            isDark = darkTheme,
            isAmoledBlack = isAmoled,
            palette = accentPalette
        )
    }

    val finalScheme = if (isAmoled && darkTheme) {
        baseScheme.copy(
            background = Color(0xFF000000),
            surface = Color(0xFF000000),
            surfaceVariant = AnyWallSurfaceVariantAmoled
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = finalScheme,
        typography = AnyWallTypography,
        content = content
    )
}
