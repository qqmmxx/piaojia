package com.qqmmxx.piaojia.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = OrangePrimary,
    onPrimary = OrangeOnPrimary,
    primaryContainer = OrangePrimaryContainer,
    onPrimaryContainer = OrangeOnPrimaryContainer,
    inversePrimary = OrangeInversePrimary,
    secondary = OrangeSecondary,
    onSecondary = OrangeOnSecondary,
    secondaryContainer = OrangeSecondaryContainer,
    onSecondaryContainer = OrangeOnSecondaryContainer,
    tertiary = OrangeTertiary,
    onTertiary = OrangeOnTertiary,
    tertiaryContainer = OrangeTertiaryContainer,
    onTertiaryContainer = OrangeOnTertiaryContainer,
    error = OrangeError,
    onError = OrangeOnError,
    errorContainer = OrangeErrorContainer,
    onErrorContainer = OrangeOnErrorContainer,
    background = OrangeBackground,
    onBackground = OrangeOnBackground,
    surface = OrangeSurface,
    onSurface = OrangeOnSurface,
    surfaceVariant = OrangeSurfaceVariant,
    onSurfaceVariant = OrangeOnSurfaceVariant,
    outline = OrangeOutline,
    outlineVariant = OrangeOutlineVariant,
    inverseSurface = OrangeInverseSurface,
    inverseOnSurface = OrangeInverseOnSurface,
    // surfaceTint 默认是 Material 基线的紫色，不显式指定的话 Elevation 会有淡紫染色
    surfaceTint = OrangePrimary,
    surfaceDim = OrangeSurfaceDim,
    surfaceBright = OrangeSurfaceBright,
    surfaceContainerLowest = OrangeSurfaceContainerLowest,
    surfaceContainerLow = OrangeSurfaceContainerLow,
    surfaceContainer = OrangeSurfaceContainer,
    surfaceContainerHigh = OrangeSurfaceContainerHigh,
    surfaceContainerHighest = OrangeSurfaceContainerHighest
)

private val DarkColors = darkColorScheme(
    primary = OrangePrimaryDark,
    onPrimary = OrangeOnPrimaryDark,
    primaryContainer = OrangePrimaryContainerDark,
    onPrimaryContainer = OrangeOnPrimaryContainerDark,
    inversePrimary = OrangePrimary,
    secondary = OrangeSecondaryDark,
    onSecondary = OrangeOnSecondaryDark,
    secondaryContainer = OrangeSecondaryContainerDark,
    onSecondaryContainer = OrangeOnSecondaryContainerDark,
    tertiary = OrangeTertiaryDark,
    onTertiary = OrangeOnTertiaryDark,
    tertiaryContainer = OrangeTertiaryContainerDark,
    onTertiaryContainer = OrangeOnTertiaryContainerDark,
    error = OrangeErrorDark,
    onError = OrangeOnErrorDark,
    errorContainer = OrangeErrorContainerDark,
    onErrorContainer = OrangeOnErrorContainerDark,
    background = OrangeBackgroundDark,
    onBackground = OrangeOnBackgroundDark,
    surface = OrangeSurfaceDark,
    onSurface = OrangeOnSurfaceDark,
    surfaceVariant = OrangeSurfaceVariantDark,
    onSurfaceVariant = OrangeOnSurfaceVariantDark,
    outline = OrangeOutlineDark,
    outlineVariant = OrangeOutlineVariantDark,
    inverseSurface = OrangeInverseSurfaceDark,
    inverseOnSurface = OrangeInverseOnSurfaceDark,
    surfaceTint = OrangePrimaryDark,
    surfaceDim = OrangeSurfaceDimDark,
    surfaceBright = OrangeSurfaceBrightDark,
    surfaceContainerLowest = OrangeSurfaceContainerLowestDark,
    surfaceContainerLow = OrangeSurfaceContainerLowDark,
    surfaceContainer = OrangeSurfaceContainerDark,
    surfaceContainerHigh = OrangeSurfaceContainerHighDark,
    surfaceContainerHighest = OrangeSurfaceContainerHighestDark
)

/**
 * @param dynamicColor 默认关闭。之前默认开启，颜色完全跟着壁纸走，
 *   壁纸偏青整个 App 就变浅青色 —— 想要固定的橘黄必须关掉它。
 */
@Composable
fun FaP2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
