package com.qqmmxx.piaojia.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 由用户选定的种子色生成整套 Material 3 配色。
 *
 * 为什么不用 Material 官方的色调映射（HCT 那套 tone 40 / 90）：
 * 官方为了保证白字对比度会把主色大幅压暗 —— 橘色种子 #E8710A 会被算成 #994700（偏棕），
 * 于是「选的颜色」和「界面上看到的颜色」对不上，这正是之前配色怎么调都觉得别扭的原因。
 *
 * 这里改成「选什么色，主色就是什么色」：
 * - 主色直接用用户选的颜色；只有对比度不达标时才自动微调明度，保证文字仍可读
 * - 容器色（顶栏、FAB 底色）是该色相的浅色调
 * - 中性面（背景、卡片、弹窗）按种子色的色相做极轻微染色，避免出现与主色不搭的灰
 */
object ThemeColors {

    private const val MIN_CONTRAST = 4.5f

    fun build(seed: Color, dark: Boolean): ColorScheme =
        if (dark) darkScheme(seed) else lightScheme(seed)

    // ---------- 浅色 ----------

    private fun lightScheme(seed: Color): ColorScheme {
        val (hue, sat, _) = seed.toHsl()

        val background = hsl(hue, sat * 0.12f, 0.985f)
        val onBackground = hsl(hue, sat * 0.16f, 0.11f)

        // 主色保持用户选的原色，只在对比度不足时微调
        val primary = readableAccent(hue, sat, seed.luminance(), background, MIN_CONTRAST)
        val primaryContainer = hsl(hue, sat, 0.89f)
        val onPrimaryContainer = hsl(hue, sat * 0.95f, 0.15f)

        return lightColorScheme(
            primary = primary,
            onPrimary = bestOnColor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = hsl(hue, sat, 0.78f),
            secondary = hsl(hue, sat * 0.35f, 0.36f),
            onSecondary = Color.White,
            secondaryContainer = hsl(hue, sat * 0.60f, 0.89f),
            onSecondaryContainer = hsl(hue, sat * 0.50f, 0.15f),
            tertiary = hsl(hue + 45f, sat * 0.45f, 0.36f),
            onTertiary = Color.White,
            tertiaryContainer = hsl(hue + 45f, sat * 0.65f, 0.89f),
            onTertiaryContainer = hsl(hue + 45f, sat * 0.55f, 0.15f),
            error = ErrorLight,
            onError = Color.White,
            errorContainer = ErrorContainerLight,
            onErrorContainer = OnErrorContainerLight,
            background = background,
            onBackground = onBackground,
            surface = background,
            onSurface = onBackground,
            surfaceVariant = hsl(hue, sat * 0.15f, 0.90f),
            onSurfaceVariant = hsl(hue, sat * 0.12f, 0.32f),
            outline = hsl(hue, sat * 0.10f, 0.50f),
            outlineVariant = hsl(hue, sat * 0.14f, 0.80f),
            inverseSurface = hsl(hue, sat * 0.16f, 0.20f),
            inverseOnSurface = hsl(hue, sat * 0.10f, 0.95f),
            surfaceTint = primary,
            surfaceDim = hsl(hue, sat * 0.12f, 0.87f),
            surfaceBright = hsl(hue, sat * 0.12f, 0.985f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hsl(hue, sat * 0.12f, 0.965f),
            surfaceContainer = hsl(hue, sat * 0.12f, 0.945f),
            surfaceContainerHigh = hsl(hue, sat * 0.12f, 0.925f),
            surfaceContainerHighest = hsl(hue, sat * 0.12f, 0.905f)
        )
    }

    // ---------- 深色 ----------

    private fun darkScheme(seed: Color): ColorScheme {
        val (hue, sat, _) = seed.toHsl()

        val background = hsl(hue, sat * 0.16f, 0.09f)
        val onBackground = hsl(hue, sat * 0.10f, 0.92f)
        val primary = readableAccent(hue, sat, 0.76f, background, MIN_CONTRAST)

        return darkColorScheme(
            primary = primary,
            onPrimary = bestOnColor(primary),
            primaryContainer = hsl(hue, sat * 0.90f, 0.29f),
            onPrimaryContainer = hsl(hue, sat * 0.85f, 0.90f),
            inversePrimary = hsl(hue, sat, 0.40f),
            secondary = hsl(hue, sat * 0.30f, 0.78f),
            onSecondary = hsl(hue, sat * 0.35f, 0.18f),
            secondaryContainer = hsl(hue, sat * 0.35f, 0.30f),
            onSecondaryContainer = hsl(hue, sat * 0.40f, 0.90f),
            tertiary = hsl(hue + 45f, sat * 0.40f, 0.78f),
            onTertiary = hsl(hue + 45f, sat * 0.45f, 0.18f),
            tertiaryContainer = hsl(hue + 45f, sat * 0.45f, 0.30f),
            onTertiaryContainer = hsl(hue + 45f, sat * 0.50f, 0.90f),
            error = ErrorDark,
            onError = OnErrorDark,
            errorContainer = ErrorContainerDark,
            onErrorContainer = OnErrorContainerDark,
            background = background,
            onBackground = onBackground,
            surface = background,
            onSurface = onBackground,
            surfaceVariant = hsl(hue, sat * 0.14f, 0.30f),
            onSurfaceVariant = hsl(hue, sat * 0.12f, 0.84f),
            outline = hsl(hue, sat * 0.10f, 0.60f),
            outlineVariant = hsl(hue, sat * 0.12f, 0.30f),
            inverseSurface = hsl(hue, sat * 0.10f, 0.92f),
            inverseOnSurface = hsl(hue, sat * 0.16f, 0.18f),
            surfaceTint = primary,
            surfaceDim = hsl(hue, sat * 0.16f, 0.09f),
            surfaceBright = hsl(hue, sat * 0.12f, 0.26f),
            surfaceContainerLowest = hsl(hue, sat * 0.16f, 0.07f),
            surfaceContainerLow = hsl(hue, sat * 0.15f, 0.13f),
            surfaceContainer = hsl(hue, sat * 0.15f, 0.16f),
            surfaceContainerHigh = hsl(hue, sat * 0.14f, 0.21f),
            surfaceContainerHighest = hsl(hue, sat * 0.13f, 0.26f)
        )
    }

    // ---------- 内部工具 ----------

    /** 对比度不足时逐步调整明度直到达标，尽量把用户选的颜色保住 */
    private fun readableAccent(
        hue: Float,
        sat: Float,
        startLightness: Float,
        against: Color,
        minRatio: Float
    ): Color {
        val lightBackground = against.luminance() > 0.5f
        var lightness = startLightness
        var candidate = hsl(hue, sat, lightness)

        var guard = 0
        while (contrastRatio(candidate, against) < minRatio && guard < 60) {
            lightness += if (lightBackground) -0.01f else 0.01f
            if (lightness <= 0f || lightness >= 1f) break
            candidate = hsl(hue, sat, lightness)
            guard++
        }
        return candidate
    }

    /** 白字还是深色字，取对比度更高的那个 */
    private fun bestOnColor(color: Color): Color =
        if (contrastRatio(color, Color.White) >= contrastRatio(color, Color(0xFF1A1A1A))) {
            Color.White
        } else {
            Color(0xFF1A1A1A)
        }

    private fun contrastRatio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }
}

// ---------- 顶层工具函数 ----------

internal fun hsl(hue: Float, saturation: Float, lightness: Float): Color =
    Color.hsl(
        hue = ((hue % 360f) + 360f) % 360f,
        saturation = saturation.coerceIn(0f, 1f),
        lightness = lightness.coerceIn(0f, 1f)
    )

/** RGB -> HSL，用于把已有颜色分解出「色相 / 饱和度 / 明度」 */
internal fun Color.toHsl(): Triple<Float, Float, Float> {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val lightness = (max + min) / 2f
    if (max == min) return Triple(0f, 0f, lightness)

    val d = max - min
    val saturation = if (lightness > 0.5f) d / (2f - max - min) else d / (max + min)
    val hue = when (max) {
        r -> ((g - b) / d + if (g < b) 6f else 0f)
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * 60f
    return Triple(hue, saturation, lightness)
}

/** 只换色相，保留明度与饱和度，用于色相滑块 */
internal fun Color.withHue(newHue: Float): Color {
    val (_, s, l) = toHsl()
    return hsl(newHue, s, l)
}

/** 预设配色：色相分布均匀，且在浅色界面上都好看 */
data class ThemePreset(val name: String, val color: Color)

val ThemePresets: List<ThemePreset> = listOf(
    ThemePreset("橘黄", Color(0xFFE8710A)),
    ThemePreset("暖橙", Color(0xFFF4511E)),
    ThemePreset("琥珀", Color(0xFFF9A825)),
    ThemePreset("草绿", Color(0xFF7CB342)),
    ThemePreset("森绿", Color(0xFF2E7D32)),
    ThemePreset("青碧", Color(0xFF009688)),
    ThemePreset("天蓝", Color(0xFF2196F3)),
    ThemePreset("靛蓝", Color(0xFF3F51B5)),
    ThemePreset("紫罗兰", Color(0xFF7E57C2)),
    ThemePreset("玫红", Color(0xFFE91E63)),
    ThemePreset("朱红", Color(0xFFD32F2F)),
    ThemePreset("石墨", Color(0xFF546E7A))
)

/** 默认配色：橘黄 */
val DefaultThemeSeed: Color = ThemePresets.first().color
