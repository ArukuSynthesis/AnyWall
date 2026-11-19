package com.anywall.converter.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AnyWallBluePrimaryDark,
    onPrimary = AnyWallBlueOnPrimaryDark,
    primaryContainer = AnyWallBlueContainerDark,
    onPrimaryContainer = AnyWallBlueOnContainerDark,
    secondary = AnyWallIndigoSecondaryDark,
    onSecondary = AnyWallIndigoOnSecondaryDark,
    secondaryContainer = AnyWallIndigoContainerDark,
    onSecondaryContainer = AnyWallIndigoOnContainerDark,
    tertiary = AnyWallEmeraldTertiaryDark,
    onTertiary = AnyWallEmeraldOnTertiaryDark,
    tertiaryContainer = AnyWallEmeraldContainerDark,
    onTertiaryContainer = AnyWallEmeraldOnContainerDark,
    background = AnyWallBackgroundDark,
    surface = AnyWallSurfaceDark,
    surfaceVariant = AnyWallSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = AnyWallBluePrimaryLight,
    onPrimary = AnyWallBlueOnPrimaryLight,
    primaryContainer = AnyWallBlueContainerLight,
    onPrimaryContainer = AnyWallBlueOnContainerLight,
    secondary = AnyWallIndigoSecondaryLight,
    onSecondary = AnyWallIndigoOnSecondaryLight,
    secondaryContainer = AnyWallIndigoContainerLight,
    onSecondaryContainer = AnyWallIndigoOnContainerLight,
    tertiary = AnyWallEmeraldTertiaryLight,
    onTertiary = AnyWallEmeraldOnTertiaryLight,
    tertiaryContainer = AnyWallEmeraldContainerLight,
    onTertiaryContainer = AnyWallEmeraldOnContainerLight,
    background = AnyWallBackgroundLight,
    surface = AnyWallSurfaceLight,
    surfaceVariant = AnyWallSurfaceVariantLight
)

/**
 * Tema Material 3 de AnyWall con soporte para tema claro/oscuro y colores dinámicos (Android 12+).
 */
@Composable
fun AnyWallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AnyWallTypography,
        content = content
    )
}
