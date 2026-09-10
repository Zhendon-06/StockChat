package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.model.AppearanceSettings
import com.guet.liang.stockchat.model.BackgroundPreset
import com.guet.liang.stockchat.model.ChatBackgroundSettings
import com.guet.liang.stockchat.model.ChatTextColorMode
import com.guet.liang.stockchat.model.FontSizeSettings
import com.guet.liang.stockchat.model.ThemeMode
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.reactive.handler.observable

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal object StockChatTheme {
    // Semantic color tokens centralized for shared UI components.
    internal const val COLOR_FFFF00FFL = 0xFFFF00FFL
    internal const val COLOR_FF0000FFL = 0xFF0000FFL
    internal const val COLOR_FF00FFFFL = 0xFF00FFFFL
    internal const val COLOR_FF00FF00L = 0xFF00FF00L
    internal const val COLOR_FFFFFF00L = 0xFFFFFF00L
    internal const val COLOR_FFFF0000L = 0xFFFF0000L
    internal const val COLOR_FF000000L = 0xFF000000L
    internal const val COLOR_FF000000 = 0xFF000000
    internal const val COLOR_FF0000FF = 0xFF0000FF
    internal const val COLOR_FF00FF00 = 0xFF00FF00
    internal const val COLOR_FF00FFFF = 0xFF00FFFF
    internal const val COLOR_FF13A87A = 0xFF13A87A
    internal const val COLOR_FF141A18 = 0xFF141A18
    internal const val COLOR_FF171A18 = 0xFF171A18
    internal const val COLOR_FF173B32 = 0xFF173B32
    internal const val COLOR_FF174C3C = 0xFF174C3C
    internal const val COLOR_FF176D57 = 0xFF176D57
    internal const val COLOR_FF1A73E8 = 0xFF1A73E8
    internal const val COLOR_FF1C2532 = 0xFF1C2532
    internal const val COLOR_FF1D2027 = 0xFF1D2027
    internal const val COLOR_FF1E3A5F = 0xFF1E3A5F
    internal const val COLOR_FF1F4B86 = 0xFF1F4B86
    internal const val COLOR_FF222831 = 0xFF222831
    internal const val COLOR_FF222925 = 0xFF222925
    internal const val COLOR_FF252826 = 0xFF252826
    internal const val COLOR_FF252D29 = 0xFF252D29
    internal const val COLOR_FF2A3445 = 0xFF2A3445
    internal const val COLOR_FF2D8B68 = 0xFF2D8B68
    internal const val COLOR_FF303A35 = 0xFF303A35
    internal const val COLOR_FF32C9AA = 0xFF32C9AA
    internal const val COLOR_FF334155 = 0xFF334155
    internal const val COLOR_FF356AA0 = 0xFF356AA0
    internal const val COLOR_FF35D1A2 = 0xFF35D1A2
    internal const val COLOR_FF374151 = 0xFF374151
    internal const val COLOR_FF394253 = 0xFF394253
    internal const val COLOR_FF3D6FB4 = 0xFF3D6FB4
    internal const val COLOR_FF414C46 = 0xFF414C46
    internal const val COLOR_FF42C79E = 0xFF42C79E
    internal const val COLOR_FF43D7BB = 0xFF43D7BB
    internal const val COLOR_FF475569 = 0xFF475569
    internal const val COLOR_FF64748B = 0xFF64748B
    internal const val COLOR_FF6EE7B7 = 0xFF6EE7B7
    internal const val COLOR_FF729E8B = 0xFF729E8B
    internal const val COLOR_FF76B7FF = 0xFF76B7FF
    internal const val COLOR_FF93C5FD = 0xFF93C5FD
    internal const val COLOR_FF94A3B8 = 0xFF94A3B8
    internal const val COLOR_FFB96016 = 0xFFB96016
    internal const val COLOR_FFBDE0D0 = 0xFFBDE0D0
    internal const val COLOR_FFBFD8F7 = 0xFFBFD8F7
    internal const val COLOR_FFC8EBDD = 0xFFC8EBDD
    internal const val COLOR_FFCBD5E1 = 0xFFCBD5E1
    internal const val COLOR_FFD84943 = 0xFFD84943
    internal const val COLOR_FFD9DFDC = 0xFFD9DFDC
    internal const val COLOR_FFDDEBFF = 0xFFDDEBFF
    internal const val COLOR_FFDDF5EC = 0xFFDDF5EC
    internal const val COLOR_FFE2A13C = 0xFFE2A13C
    internal const val COLOR_FFE4EAE7 = 0xFFE4EAE7
    internal const val COLOR_FFE7B35A = 0xFFE7B35A
    internal const val COLOR_FFE8ECE9 = 0xFFE8ECE9
    internal const val COLOR_FFE8F7F1 = 0xFFE8F7F1
    internal const val COLOR_FFE9EEF4 = 0xFFE9EEF4
    internal const val COLOR_FFEAF2FF = 0xFFEAF2FF
    internal const val COLOR_FFF1F1F1 = 0xFFF1F1F1
    internal const val COLOR_FFF2A98E = 0xFFF2A98E
    internal const val COLOR_FFF2F3F1 = 0xFFF2F3F1
    internal const val COLOR_FFF2F5F8 = 0xFFF2F5F8
    internal const val COLOR_FFF39A18 = 0xFFF39A18
    internal const val COLOR_FFF3F7F5 = 0xFFF3F7F5
    internal const val COLOR_FFF3F7FA = 0xFFF3F7FA
    internal const val COLOR_FFF5F6F5 = 0xFFF5F6F5
    internal const val COLOR_FFF8FAF9 = 0xFFF8FAF9
    internal const val COLOR_FFFF0000 = 0xFFFF0000
    internal const val COLOR_FFFF00FF = 0xFFFF00FF
    internal const val COLOR_FFFF746D = 0xFFFF746D
    internal const val COLOR_FFFFE0BE = 0xFFFFE0BE
    internal const val COLOR_FFFFFF00 = 0xFFFFFF00
    internal const val COLOR_FFFFFFFF = 0xFFFFFFFF
    // 主题是跨页面共享的进程级单例，需要让「当前正在收集依赖的 Pager」都能观察到这几项状态；
    // 替代 API PagerScope.observable 会把状态绑定到单个 Pager，不适用于这里，故保留全局 observable。
    @Suppress("DEPRECATION") private var appearanceState by observable(AppearanceSettings())
    @Suppress("DEPRECATION") private var systemDarkState by observable(false)

    @Suppress("DEPRECATION")
    internal var renderRevision by observable(0)
        private set

    val isDark: Boolean
        get() =
            when (appearanceState.themeMode) {
                ThemeMode.SYSTEM -> systemDarkState
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

    val themeMode: ThemeMode
        get() = appearanceState.themeMode

    val fontSizeSettings: FontSizeSettings
        get() = appearanceState.fontSize

    val fontScale: Float
        get() =
            if (fontSizeSettings.followsSystem) {
                1f
            } else {
                fontSizeSettings.scale.coerceIn(FontSizeSettings.MIN_SCALE, FontSizeSettings.MAX_SCALE)
            }

    val backgroundPreset: BackgroundPreset
        get() = appearanceState.chatBackground.preset

    val backgroundImageUri: String?
        get() = appearanceState.chatBackground.customImageUri

    val backgroundBlurRadius: Float
        get() = appearanceState.chatBackground.blurRadius

    val backgroundMask: Color
        get() {
            if (usesLegacyDefaultBackground) {
                return Color(0x00000000)
            }
            val contrast = backgroundContrast()
            return Color(contrast.maskColorArgb, contrast.maskAlpha)
        }

    val chatTextSizeSp: Float
        get() = appearanceState.chatBackground.chatTextSizeSp

    val chatTextColorMode: ChatTextColorMode
        get() = appearanceState.chatBackground.chatTextColorMode

    val chatTextColorArgb: Long
        get() =
            when (chatTextColorMode) {
                ChatTextColorMode.AUTOMATIC -> backgroundContrast().textColorArgb
                ChatTextColorMode.LIGHT -> 0xFFFFFFFF
                ChatTextColorMode.DARK -> 0xFF222831
                ChatTextColorMode.BLUE -> if (isDark) 0xFF8FC2FF else 0xFF1F4B86
                ChatTextColorMode.GREEN -> if (isDark) 0xFF70D8B5 else 0xFF176D57
                ChatTextColorMode.ORANGE -> if (isDark) 0xFFFFBC78 else 0xFFB96016
            }

    val chatTextColor: Color
        get() = Color(chatTextColorArgb)

    val background: Color
        get() = Color(if (isDark) DARK_APP_BACKGROUND else LIGHT_APP_BACKGROUND)

    val chatBackgroundStart: Color
        get() = backgroundColors().first

    val chatBackgroundEnd: Color
        get() = backgroundColors().second

    val backgroundSofteningMask: Color
        get() {
            if (usesLegacyDefaultBackground) {
                return Color(0x00000000)
            }
            val alpha = (backgroundBlurRadius / ChatBackgroundSettings.MAX_BLUR_RADIUS * 0.12f).coerceIn(0f, 0.12f)
            return if (isDark || backgroundPreset == BackgroundPreset.GRAPHITE) {
                Color(0xFF000000, alpha)
            } else {
                Color(0xFFFFFFFF, alpha)
            }
        }

    val surface: Color
        get() = palette().surface

    val surfaceSoft: Color
        get() = palette().surfaceSoft

    val recessed: Color
        get() = palette().recessed

    val userBubble: Color
        get() =
            when (chatTextColorMode) {
                ChatTextColorMode.LIGHT -> Color(0xD92B3430)
                ChatTextColorMode.DARK -> Color(0xE8F3F5F4)
                ChatTextColorMode.AUTOMATIC -> {
                    if (backgroundContrast().textColorArgb == CHAT_LIGHT_TEXT_ARGB) {
                        Color(0xD92B3430)
                    } else {
                        palette().userBubble
                    }
                }
                ChatTextColorMode.BLUE,
                ChatTextColorMode.GREEN,
                ChatTextColorMode.ORANGE -> palette().userBubble
            }

    val textPrimary: Color
        get() = palette().textPrimary

    val textSecondary: Color
        get() = palette().textSecondary

    val textTertiary: Color
        get() = palette().textTertiary

    val accent: Color
        get() = palette().accent

    val accentSoft: Color
        get() = palette().accentSoft

    val border: Color
        get() = palette().border

    val borderStrong: Color
        get() = palette().borderStrong

    val positive: Color
        get() = palette().positive

    val negative: Color
        get() = palette().negative

    val warningSoft: Color
        get() = palette().warningSoft

    val warning: Color
        get() = palette().warning

    val warningBorder: Color
        get() = palette().warningBorder

    val typingActive: Color
        get() = palette().typingActive

    val typingInactive: Color
        get() = palette().typingInactive

    val marketMoodBackgroundStart: Color
        get() = palette().marketMoodBackgroundStart

    val marketMoodBackgroundEnd: Color
        get() = palette().marketMoodBackgroundEnd

    val marketMoodBorder: Color
        get() = palette().marketMoodBorder

    val marketPositiveSoft: Color
        get() = palette().marketPositiveSoft

    val marketNegativeSoft: Color
        get() = palette().marketNegativeSoft

    fun applyAppearance(appearance: AppearanceSettings, systemDark: Boolean) {
        // renderRevision 翻转会让聊天页整棵内容树重建（vif/velse 切换），滚动位置随之丢失。
        // 页面每次回到前台都会调用这里，所以只有外观真正变化时才触发重建。
        if (appearanceState == appearance && systemDarkState == systemDark) {
            return
        }
        appearanceState = appearance
        systemDarkState = systemDark
        renderRevision += 1
    }

    private fun palette(): StockChatPalette = if (isDark) DarkPalette else LightPalette

    private fun backgroundContrast(): ChatBackgroundContrast {
        val colors = backgroundColorValues()
        val settings =
            if (usesLegacyDefaultBackground) {
                appearanceState.chatBackground.copy(
                    blurRadius = ChatBackgroundSettings.MIN_BLUR_RADIUS,
                    maskOpacity = ChatBackgroundSettings.MIN_MASK_OPACITY,
                    maskBrightness = ChatBackgroundSettings.DEFAULT_MASK_BRIGHTNESS,
                )
            } else {
                appearanceState.chatBackground
            }
        return resolveChatBackgroundContrast(
            settings = settings,
            backgroundStartArgb = colors.first,
            backgroundEndArgb = colors.second,
            darkSofteningMask = isDark || backgroundPreset == BackgroundPreset.GRAPHITE,
        )
    }

    private fun backgroundColors(): Pair<Color, Color> {
        val colors = backgroundColorValues()
        return Color(colors.first) to Color(colors.second)
    }

    private fun backgroundColorValues(): Pair<Long, Long> =
        if (usesLegacyDefaultBackground) {
            val color = if (isDark) DARK_APP_BACKGROUND else LIGHT_APP_BACKGROUND
            color to color
        } else
            when (backgroundPreset) {
                BackgroundPreset.DEFAULT ->
                    if (isDark) {
                        0xFF111510 to 0xFF171D19
                    } else {
                        0xFFF6F7F4 to 0xFFEEF2EF
                    }
                BackgroundPreset.MARKET_BLUE ->
                    if (isDark) {
                        0xFF132238 to 0xFF1D3553
                    } else {
                        0xFFE5F0FF to 0xFFCFE2FA
                    }
                BackgroundPreset.GRAPHITE ->
                    if (isDark) {
                        0xFF151B24 to 0xFF222C39
                    } else {
                        0xFF394253 to 0xFF26303E
                    }
                BackgroundPreset.FOREST ->
                    if (isDark) {
                        0xFF10251E to 0xFF18372D
                    } else {
                        0xFFDDEFE7 to 0xFFBDDCCF
                    }
                BackgroundPreset.SUNSET ->
                    if (isDark) {
                        0xFF302019 to 0xFF422820
                    } else {
                        0xFFFFEAD6 to 0xFFF8C9B5
                    }
            }

    private val usesLegacyDefaultBackground: Boolean
        get() = backgroundPreset == BackgroundPreset.DEFAULT && backgroundImageUri.isNullOrBlank()

    private data class StockChatPalette(
        val surface: Color,
        val surfaceSoft: Color,
        val recessed: Color,
        val userBubble: Color,
        val textPrimary: Color,
        val textSecondary: Color,
        val textTertiary: Color,
        val accent: Color,
        val accentSoft: Color,
        val border: Color,
        val borderStrong: Color,
        val positive: Color,
        val negative: Color,
        val warningSoft: Color,
        val warning: Color,
        val warningBorder: Color,
        val typingActive: Color,
        val typingInactive: Color,
        val marketMoodBackgroundStart: Color,
        val marketMoodBackgroundEnd: Color,
        val marketMoodBorder: Color,
        val marketPositiveSoft: Color,
        val marketNegativeSoft: Color,
    )

    private val LightPalette =
        StockChatPalette(
            surface = Color.WHITE,
            surfaceSoft = Color(0xFFFBFCFB),
            recessed = Color(0xFFECEFED),
            userBubble = Color(0xFFE2E4E5),
            textPrimary = Color(LIGHT_TEXT_PRIMARY),
            textSecondary = Color(0xFF6F7672),
            textTertiary = Color(0xFFA1A7A3),
            accent = Color(0xFF13A87A),
            accentSoft = Color(0xFFE8F7F1),
            border = Color(0xFFE4E8E5),
            borderStrong = Color(0xFFD9DFDC),
            positive = Color(0xFFD84A43),
            negative = Color(0xFF168765),
            warningSoft = Color(0xFFFFF5E6),
            warning = Color(0xFF9B6A18),
            warningBorder = Color(0xFFF2D9AE),
            typingActive = Color(0xFF4A4F4C),
            typingInactive = Color(0xFFB5BAB6),
            marketMoodBackgroundStart = Color(0xFFEAF8F2),
            marketMoodBackgroundEnd = Color(0xFFF7FBF8),
            marketMoodBorder = Color(0xFFD7EDE2),
            marketPositiveSoft = Color(0xFFFDEEED),
            marketNegativeSoft = Color(0xFFE9F5F1),
        )

    private val DarkPalette =
        StockChatPalette(
            surface = Color(0xFF1B211E),
            surfaceSoft = Color(0xFF222925),
            recessed = Color(0xFF2E3732),
            userBubble = Color(0xFF2B3430),
            textPrimary = Color(DARK_TEXT_PRIMARY),
            textSecondary = Color(0xFFB4BDB8),
            textTertiary = Color(0xFF7F8A84),
            accent = Color(0xFF35D1A2),
            accentSoft = Color(0xFF173B32),
            border = Color(0xFF303A35),
            borderStrong = Color(0xFF414C46),
            positive = Color(0xFFFF746D),
            negative = Color(0xFF42C79E),
            warningSoft = Color(0xFF3A2D18),
            warning = Color(0xFFF0BE68),
            warningBorder = Color(0xFF624C29),
            typingActive = Color(0xFFD8E0DC),
            typingInactive = Color(0xFF626C67),
            marketMoodBackgroundStart = Color(0xFF1D3A30),
            marketMoodBackgroundEnd = Color(0xFF18251F),
            marketMoodBorder = Color(0xFF345447),
            marketPositiveSoft = Color(0xFF4A2928),
            marketNegativeSoft = Color(0xFF173B32),
        )

    private const val LIGHT_APP_BACKGROUND = 0xFFF6F7F4
    private const val DARK_APP_BACKGROUND = 0xFF111510
    private const val LIGHT_TEXT_PRIMARY = CHAT_DARK_TEXT_ARGB
    private const val DARK_TEXT_PRIMARY = CHAT_LIGHT_TEXT_ARGB
}

internal fun stockChatThemedAsset(lightAsset: String, darkAsset: String): String {
    return if (StockChatTheme.isDark) darkAsset else lightAsset
}

/** 把持久化的外观设置应用到主题：页面创建、回到前台与系统主题变化时调用。 */
internal fun BasePager.applySavedAppearance() {
    StockChatTheme.applyAppearance(appearance = savedSettingsSnapshot().appearance, systemDark = isNightMode())
}
