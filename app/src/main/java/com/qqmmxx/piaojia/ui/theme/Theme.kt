package com.qqmmxx.piaojia.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * @param seed 主题种子色，由用户在「设置 → 配色」里选择，默认橘黄。
 *   整套配色由 [ThemeColors] 从它推导，不再跟随系统壁纸。
 * @param dynamicColor 保留开关，默认关闭。开启后 Android 12+ 会用壁纸动态取色，
 *   此时 [seed] 被忽略。
 */
@Composable
fun FaP2Theme(
    seed: Color = DefaultThemeSeed,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> remember(seed, darkTheme) { ThemeColors.build(seed, darkTheme) }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
