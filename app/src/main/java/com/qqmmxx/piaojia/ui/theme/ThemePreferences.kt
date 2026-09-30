package com.qqmmxx.piaojia.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 配色选择落盘。用 SharedPreferences，不引入 DataStore 之类的额外依赖。
 */
object ThemePreferences {

    private const val FILE_NAME = "theme_prefs"
    private const val KEY_SEED = "seed_color"

    fun load(context: Context): Color {
        val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        val argb = prefs.getInt(KEY_SEED, DefaultThemeSeed.toArgb())
        return Color(argb)
    }

    fun save(context: Context, seed: Color) {
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SEED, seed.toArgb())
            .apply()
    }
}
