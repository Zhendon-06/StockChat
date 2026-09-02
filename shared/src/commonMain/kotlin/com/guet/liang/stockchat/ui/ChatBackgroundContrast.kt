package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.ChatBackgroundSettings
import com.guet.liang.stockchat.model.ChatTextColorMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal const val CHAT_DARK_TEXT_ARGB = 0xFF171A18
internal const val CHAT_LIGHT_TEXT_ARGB = 0xFFF3F7F5

internal data class ChatBackgroundContrast(
    val textColorArgb: Long,
    val maskColorArgb: Long,
    val maskAlpha: Float,
)

internal fun resolveChatBackgroundContrast(
    settings: ChatBackgroundSettings,
    backgroundStartArgb: Long,
    backgroundEndArgb: Long,
    darkSofteningMask: Boolean,
): ChatBackgroundContrast {
    val brightnessDistance = (settings.maskBrightness - 1f).coerceIn(-0.4f, 0.4f)
    val maskColorArgb = if (brightnessDistance >= 0f) WHITE_ARGB else BLACK_ARGB
    val configuredMaskAlpha = (
        settings.maskOpacity + abs(brightnessDistance) * MASK_BRIGHTNESS_ALPHA_FACTOR
    ).coerceIn(0f, MAX_MASK_ALPHA)
    val needsGuaranteedImageContrast =
        settings.chatTextColorMode == ChatTextColorMode.AUTOMATIC &&
            !settings.customImageUri.isNullOrBlank()
    val maskAlpha = if (needsGuaranteedImageContrast) {
        configuredMaskAlpha.coerceAtLeast(MIN_CUSTOM_IMAGE_AUTO_MASK_ALPHA)
    } else {
        configuredMaskAlpha
    }
    val softeningMaskArgb = if (darkSofteningMask) BLACK_ARGB else WHITE_ARGB
    val softeningAlpha = (
        settings.blurRadius / ChatBackgroundSettings.MAX_BLUR_RADIUS * MAX_SOFTENING_ALPHA
    ).coerceIn(0f, MAX_SOFTENING_ALPHA)
    val backgrounds = if (needsGuaranteedImageContrast) {
        listOf(BLACK_ARGB, WHITE_ARGB)
    } else {
        listOf(backgroundStartArgb, backgroundEndArgb)
    }
    val effectiveBackgrounds = backgrounds.map { backgroundArgb ->
        Rgb.fromArgb(backgroundArgb)
            .overlay(Rgb.fromArgb(softeningMaskArgb), softeningAlpha)
            .overlay(Rgb.fromArgb(maskColorArgb), maskAlpha)
    }
    val darkTextLuminance = Rgb.fromArgb(CHAT_DARK_TEXT_ARGB).relativeLuminance()
    val lightTextLuminance = Rgb.fromArgb(CHAT_LIGHT_TEXT_ARGB).relativeLuminance()
    val darkTextMinimumContrast = effectiveBackgrounds.minOf { background ->
        contrastRatio(darkTextLuminance, background.relativeLuminance())
    }
    val lightTextMinimumContrast = effectiveBackgrounds.minOf { background ->
        contrastRatio(lightTextLuminance, background.relativeLuminance())
    }
    return ChatBackgroundContrast(
        textColorArgb = if (lightTextMinimumContrast > darkTextMinimumContrast) {
            CHAT_LIGHT_TEXT_ARGB
        } else {
            CHAT_DARK_TEXT_ARGB
        },
        maskColorArgb = maskColorArgb,
        maskAlpha = maskAlpha,
    )
}

private data class Rgb(
    val red: Double,
    val green: Double,
    val blue: Double,
) {
    fun overlay(overlay: Rgb, alpha: Float): Rgb {
        val normalizedAlpha = alpha.coerceIn(0f, 1f).toDouble()
        val baseAlpha = 1.0 - normalizedAlpha
        return Rgb(
            red = red * baseAlpha + overlay.red * normalizedAlpha,
            green = green * baseAlpha + overlay.green * normalizedAlpha,
            blue = blue * baseAlpha + overlay.blue * normalizedAlpha,
        )
    }

    fun relativeLuminance(): Double =
        RELATIVE_LUMINANCE_RED * red.toLinearComponent() +
            RELATIVE_LUMINANCE_GREEN * green.toLinearComponent() +
            RELATIVE_LUMINANCE_BLUE * blue.toLinearComponent()

    companion object {
        fun fromArgb(argb: Long): Rgb = Rgb(
            red = ((argb shr 16) and 0xFF).toDouble() / 255.0,
            green = ((argb shr 8) and 0xFF).toDouble() / 255.0,
            blue = (argb and 0xFF).toDouble() / 255.0,
        )
    }
}

private fun Double.toLinearComponent(): Double = if (this <= SRGB_LINEAR_THRESHOLD) {
    this / SRGB_LINEAR_DIVISOR
} else {
    ((this + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_EXPONENT)
}

private fun contrastRatio(firstLuminance: Double, secondLuminance: Double): Double =
    (max(firstLuminance, secondLuminance) + CONTRAST_OFFSET) /
        (min(firstLuminance, secondLuminance) + CONTRAST_OFFSET)

private const val BLACK_ARGB = 0xFF000000
private const val WHITE_ARGB = 0xFFFFFFFF
private const val MASK_BRIGHTNESS_ALPHA_FACTOR = 0.34f
private const val MAX_MASK_ALPHA = 0.82f
private const val MIN_CUSTOM_IMAGE_AUTO_MASK_ALPHA = 0.64f
private const val MAX_SOFTENING_ALPHA = 0.12f
private const val RELATIVE_LUMINANCE_RED = 0.2126
private const val RELATIVE_LUMINANCE_GREEN = 0.7152
private const val RELATIVE_LUMINANCE_BLUE = 0.0722
private const val SRGB_LINEAR_THRESHOLD = 0.04045
private const val SRGB_LINEAR_DIVISOR = 12.92
private const val SRGB_OFFSET = 0.055
private const val SRGB_SCALE = 1.055
private const val SRGB_EXPONENT = 2.4
private const val CONTRAST_OFFSET = 0.05
