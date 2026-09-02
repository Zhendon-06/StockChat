package com.guet.liang.stockchat.ui.settings

import com.tencent.kuikly.core.base.Color

internal data class SettingsPalette(
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val accent: Color,
    val accentSoft: Color,
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val warningSoft: Color,
)

internal object SettingsPalettes {
    val Light = SettingsPalette(
        background = Color(0xFFF5F6FA),
        surface = Color.WHITE,
        surfaceMuted = Color(0xFFF0F1F3),
        textPrimary = Color(0xFF1D2027),
        textSecondary = Color(0xFF6D737E),
        textTertiary = Color(0xFFA0A6AF),
        divider = Color(0xFFE9EBEF),
        accent = Color(0xFF0EAA7B),
        accentSoft = Color(0xFFE5F7F1),
        positive = Color(0xFFD84943),
        negative = Color(0xFF11805F),
        warning = Color(0xFF986814),
        warningSoft = Color(0xFFFFF5E3),
    )

    val Dark = SettingsPalette(
        background = Color(0xFF11141A),
        surface = Color(0xFF1C2028),
        surfaceMuted = Color(0xFF2A2F39),
        textPrimary = Color(0xFFF4F5F7),
        textSecondary = Color(0xFFB6BBC4),
        textTertiary = Color(0xFF7C838F),
        divider = Color(0xFF303641),
        accent = Color(0xFF35D0A2),
        accentSoft = Color(0xFF173B33),
        positive = Color(0xFFFF746D),
        negative = Color(0xFF42C79E),
        warning = Color(0xFFF0BE68),
        warningSoft = Color(0xFF3A2D18),
    )
}

internal const val SETTINGS_UI_SCALE = 0.8f

internal fun Float.settingsDp(): Float = this * SETTINGS_UI_SCALE

internal fun Int.settingsDp(): Float = this.toFloat() * SETTINGS_UI_SCALE

internal fun settingsContentWidth(
    pageWidth: Float,
    horizontalMargin: Float,
): Float = (pageWidth - horizontalMargin.settingsDp() * 2f).coerceAtLeast(1f)

internal fun settingsInset(baseInset: Float): Float = baseInset.settingsDp()
