package com.qqmmxx.piaojia.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 固定不随主题变化的错误色。
 * 其他颜色全部由 [ThemeColors] 依据用户选定的种子色生成。
 */

// 浅色
val ErrorLight = Color(0xFFBA1A1A)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

// 深色
val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)
