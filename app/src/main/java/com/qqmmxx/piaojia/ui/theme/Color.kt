package com.qqmmxx.piaojia.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 橘黄色主题配色（Material 3 角色，种子色 ≈ #E8710A）。
 *
 * 之前整个应用用的是 dynamicColor，颜色由系统从壁纸里抽，所以会随壁纸变成任意色
 * （比如壁纸偏青就整个 UI 都是浅青色）。现在固定成橘黄，不再跟随壁纸。
 */

// ---------- 浅色 ----------
val OrangePrimary = Color(0xFFE8710A)
val OrangeOnPrimary = Color(0xFFFFFFFF)
val OrangePrimaryContainer = Color(0xFFFFDCC2)
val OrangeOnPrimaryContainer = Color(0xFF2E1400)
val OrangeInversePrimary = Color(0xFFFFB77C)

val OrangeSecondary = Color(0xFF755845)
val OrangeOnSecondary = Color(0xFFFFFFFF)
val OrangeSecondaryContainer = Color(0xFFFFDCC2)
val OrangeOnSecondaryContainer = Color(0xFF2A1706)

val OrangeTertiary = Color(0xFF61603A)
val OrangeOnTertiary = Color(0xFFFFFFFF)
val OrangeTertiaryContainer = Color(0xFFE7E5B4)
val OrangeOnTertiaryContainer = Color(0xFF1D1D00)

val OrangeError = Color(0xFFBA1A1A)
val OrangeOnError = Color(0xFFFFFFFF)
val OrangeErrorContainer = Color(0xFFFFDAD6)
val OrangeOnErrorContainer = Color(0xFF410002)

val OrangeBackground = Color(0xFFFFF8F5)
val OrangeOnBackground = Color(0xFF221A14)
val OrangeSurface = Color(0xFFFFF8F5)
val OrangeOnSurface = Color(0xFF221A14)
val OrangeSurfaceVariant = Color(0xFFF4DED1)
val OrangeOnSurfaceVariant = Color(0xFF52443A)
val OrangeOutline = Color(0xFF85736A)
val OrangeOutlineVariant = Color(0xFFD7C2B5)
val OrangeInverseSurface = Color(0xFF382E29)
val OrangeInverseOnSurface = Color(0xFFFCEEE5)

// 暖色中性面。不显式覆盖的话，lightColorScheme() 会用 Material 基线的紫调灰
// （例如 surfaceContainerHigh = #ECE6F0），弹窗和卡片就会透出一层不合调的淡紫。
val OrangeSurfaceDim = Color(0xFFE4D8D1)
val OrangeSurfaceBright = Color(0xFFFFF8F5)
val OrangeSurfaceContainerLowest = Color(0xFFFFFFFF)
val OrangeSurfaceContainerLow = Color(0xFFFBF2ED)
val OrangeSurfaceContainer = Color(0xFFF5ECE6)
val OrangeSurfaceContainerHigh = Color(0xFFEFE6E0)
val OrangeSurfaceContainerHighest = Color(0xFFE9E0DA)

// ---------- 深色 ----------
val OrangePrimaryDark = Color(0xFFFFB77C)
val OrangeOnPrimaryDark = Color(0xFF4C2700)
val OrangePrimaryContainerDark = Color(0xFF6D3A00)
val OrangeOnPrimaryContainerDark = Color(0xFFFFDCC2)

val OrangeSecondaryDark = Color(0xFFE4C0A4)
val OrangeOnSecondaryDark = Color(0xFF422B16)
val OrangeSecondaryContainerDark = Color(0xFF5A412A)
val OrangeOnSecondaryContainerDark = Color(0xFFFFDCC2)

val OrangeTertiaryDark = Color(0xFFCBCA98)
val OrangeOnTertiaryDark = Color(0xFF323200)
val OrangeTertiaryContainerDark = Color(0xFF48481F)
val OrangeOnTertiaryContainerDark = Color(0xFFE7E5B4)

val OrangeErrorDark = Color(0xFFFFB4AB)
val OrangeOnErrorDark = Color(0xFF690005)
val OrangeErrorContainerDark = Color(0xFF93000A)
val OrangeOnErrorContainerDark = Color(0xFFFFDAD6)

val OrangeBackgroundDark = Color(0xFF1A120B)
val OrangeOnBackgroundDark = Color(0xFFF0DFD6)
val OrangeSurfaceDark = Color(0xFF1A120B)
val OrangeOnSurfaceDark = Color(0xFFF0DFD6)
val OrangeSurfaceVariantDark = Color(0xFF52443A)
val OrangeOnSurfaceVariantDark = Color(0xFFD7C2B5)
val OrangeOutlineDark = Color(0xFF9F8D82)
val OrangeOutlineVariantDark = Color(0xFF52443A)
val OrangeInverseSurfaceDark = Color(0xFFF0DFD6)
val OrangeInverseOnSurfaceDark = Color(0xFF382E29)

val OrangeSurfaceDimDark = Color(0xFF1A120B)
val OrangeSurfaceBrightDark = Color(0xFF423830)
val OrangeSurfaceContainerLowestDark = Color(0xFF140D07)
val OrangeSurfaceContainerLowDark = Color(0xFF221A14)
val OrangeSurfaceContainerDark = Color(0xFF271E18)
val OrangeSurfaceContainerHighDark = Color(0xFF32281F)
val OrangeSurfaceContainerHighestDark = Color(0xFF3D332A)
