package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.closePage
import com.guet.liang.stockchat.controller.SettingsController
import com.guet.liang.stockchat.model.BackgroundPreset
import com.guet.liang.stockchat.model.ChatBackgroundSettings
import com.guet.liang.stockchat.model.ChatTextColorMode
import com.guet.liang.stockchat.model.ThemeMode
import com.guet.liang.stockchat.ui.ChatBackgroundContrast
import com.guet.liang.stockchat.ui.StockChatTheme
import com.guet.liang.stockchat.ui.resolveChatBackgroundContrast
import com.guet.liang.stockchat.ui.settingsController
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import kotlin.math.roundToInt

@Page(BACKGROUND_SETTINGS_PAGE_NAME, supportInLocal = true)
/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal class BackgroundSettingsPage : BasePager() {
    private lateinit var controller: SettingsController
    internal var settings by observable(ChatBackgroundSettings())
    internal var themeMode by observable(ThemeMode.SYSTEM)

    override fun created() {
        super.created()
        controller = settingsController()
        val snapshot = controller.snapshot()
        settings = snapshot.appearance.chatBackground
        themeMode = snapshot.appearance.themeMode
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr { backgroundColor(ctx.palette().background) }
            SettingsPageHeader(
                statusBarHeight = ctx.pagerData.statusBarHeight,
                title = "背景设置",
                palette = ctx::palette,
                actionText = "完成",
                onBack = ctx::closePage,
                onAction = ctx::closePage,
            )
            Scroller {
                attr {
                    absolutePosition(
                        top = ctx.pagerData.statusBarHeight + SETTINGS_HEADER_HEIGHT,
                        left = 0f,
                        right = 0f,
                        bottom = ctx.pagerData.safeAreaInsets.bottom,
                    )
                    padding(bottom = 28f.settingsDp(), left = 0f, right = 0f, top = 0f)
                    showScrollerIndicator(false)
                    bouncesEnable(true)
                }
                ctx.BackgroundPreview(this)
                ctx.BackgroundPresetPicker(this)
                ctx.EffectAdjustments(this)
                Text {
                    attr {
                        width((ctx.pagerData.pageViewWidth - 44f.settingsDp()).coerceAtLeast(1f))
                        alignSelfCenter()
                        text("背景效果仅作用于聊天区域。行情和 AI 结论均为演示信息，仅供参考，不构成投资建议。")
                        fontSize(11f.settingsDp())
                        lineHeight(18f.settingsDp())
                        color(ctx.palette().textTertiary)
                        marginTop(16f.settingsDp())
                    }
                }
            }
        }
    }

    internal fun updateSettings(updated: ChatBackgroundSettings) {
        settings = updated
        controller.setChatBackground(updated)
    }

    internal fun chooseBackgroundImage() {
        bridgeModule.pickImages(1) pickerResult@{ result ->
            if (result == null) {
                bridgeModule.toast("图片选择暂时不可用")
                return@pickerResult
            }
            if (result.optInt("cancelled", 0) == 1) {
                return@pickerResult
            }
            if (result.optInt("success", 0) != 1) {
                bridgeModule.toast(result.optString("errorMessage").ifBlank { "背景图片选择失败" })
                return@pickerResult
            }
            val imageUri =
                result.optJSONArray("previewImages")?.optString(0).orEmpty().trim().ifBlank {
                    result.optJSONArray("images")?.optString(0).orEmpty().trim()
                }
            if (imageUri.isBlank()) {
                bridgeModule.toast("没有选择可用图片")
                return@pickerResult
            }
            updateSettings(settings.copy(customImageUri = imageUri))
        }
    }

    internal fun backgroundLabel(): String =
        if (settings.customImageUri.isNullOrBlank()) settings.preset.displayName else "自定义图片"

    internal fun isPresetSelected(preset: BackgroundPreset): Boolean =
        settings.customImageUri.isNullOrBlank() && settings.preset == preset

    internal fun palette(): SettingsPalette = settingsPalette(themeMode)

    internal fun applyPreviewGradient(attr: com.tencent.kuikly.core.base.ContainerAttr) {
        applyPresetGradient(attr, settings.preset)
    }

    internal fun applyPresetGradient(
        attr: com.tencent.kuikly.core.base.ContainerAttr,
        preset: BackgroundPreset,
    ) {
        val colors = presetGradient(preset)
        attr.backgroundLinearGradient(
            Direction.TO_BOTTOM_RIGHT,
            ColorStop(colors.first, 0f),
            ColorStop(colors.second, 1f),
        )
    }

    private fun presetGradient(preset: BackgroundPreset): Pair<Color, Color> {
        val colors = presetGradientArgb(preset)
        return Color(colors.first) to Color(colors.second)
    }

    private fun presetGradientArgb(preset: BackgroundPreset): Pair<Long, Long> =
        when (preset) {
            BackgroundPreset.DEFAULT ->
                StockChatTheme.COLOR_FFF2F5F8 to StockChatTheme.COLOR_FFE9EEF4
            BackgroundPreset.MARKET_BLUE ->
                StockChatTheme.COLOR_FFDDEBFF to StockChatTheme.COLOR_FFBFD8F7
            BackgroundPreset.GRAPHITE ->
                StockChatTheme.COLOR_FF394253 to StockChatTheme.COLOR_FF1C2532
            BackgroundPreset.FOREST ->
                StockChatTheme.COLOR_FFBDE0D0 to StockChatTheme.COLOR_FF729E8B
            BackgroundPreset.SUNSET ->
                StockChatTheme.COLOR_FFFFE0BE to StockChatTheme.COLOR_FFF2A98E
        }

    internal fun previewMaskColor(): Color {
        val contrast = previewContrast()
        return Color(contrast.maskColorArgb, contrast.maskAlpha)
    }

    internal fun previewSofteningMaskColor(): Color {
        val alpha =
            (settings.blurRadius / ChatBackgroundSettings.MAX_BLUR_RADIUS * 0.12f).coerceIn(
                0f,
                0.12f,
            )
        return if (settings.preset == BackgroundPreset.GRAPHITE) {
            Color(StockChatTheme.COLOR_FF000000, alpha)
        } else {
            Color(StockChatTheme.COLOR_FFFFFFFF, alpha)
        }
    }

    internal fun previewPrimaryTextColor(): Color =
        when (settings.chatTextColorMode) {
            ChatTextColorMode.AUTOMATIC -> Color(previewContrast().textColorArgb)
            ChatTextColorMode.LIGHT -> Color.WHITE
            ChatTextColorMode.DARK -> Color(StockChatTheme.COLOR_FF222831)
            ChatTextColorMode.BLUE -> Color(StockChatTheme.COLOR_FF1F4B86)
            ChatTextColorMode.GREEN -> Color(StockChatTheme.COLOR_FF176D57)
            ChatTextColorMode.ORANGE -> Color(StockChatTheme.COLOR_FFB96016)
        }

    private fun previewContrast(): ChatBackgroundContrast {
        val colors = presetGradientArgb(settings.preset)
        return resolveChatBackgroundContrast(
            settings = settings,
            backgroundStartArgb = colors.first,
            backgroundEndArgb = colors.second,
            darkSofteningMask = settings.preset == BackgroundPreset.GRAPHITE,
        )
    }

    internal fun textModeColor(mode: ChatTextColorMode): Color =
        when (mode) {
            ChatTextColorMode.AUTOMATIC -> palette().surfaceMuted
            ChatTextColorMode.LIGHT -> Color.WHITE
            ChatTextColorMode.DARK -> Color(StockChatTheme.COLOR_FF2A3445)
            ChatTextColorMode.BLUE -> Color(StockChatTheme.COLOR_FF3D6FB4)
            ChatTextColorMode.GREEN -> Color(StockChatTheme.COLOR_FF2D8B68)
            ChatTextColorMode.ORANGE -> Color(StockChatTheme.COLOR_FFF39A18)
        }

    internal fun formatTwoDecimals(value: Float): String =
        ((value * 100f).roundToInt() / 100f).toString()

    internal fun formatOneDecimal(value: Float): String =
        ((value * 10f).roundToInt() / 10f).toString()
}
