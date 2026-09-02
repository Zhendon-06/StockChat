package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.model.BackgroundPreset
import com.guet.liang.stockchat.model.ChatBackgroundSettings
import com.guet.liang.stockchat.model.ChatTextColorMode
import com.guet.liang.stockchat.model.ThemeMode
import com.guet.liang.stockchat.ui.ChatBackgroundContrast
import com.guet.liang.stockchat.ui.resolveChatBackgroundContrast
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.Size
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Slider
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.roundToInt

@Page(BACKGROUND_SETTINGS_PAGE_NAME, supportInLocal = true)
internal class BackgroundSettingsPage : BasePager() {
    private var settings by observable(ChatBackgroundSettings())
    private var themeMode by observable(ThemeMode.SYSTEM)

    override fun created() {
        super.created()
        val snapshot = StockChatSettingsStore.repository.loadSnapshot()
        settings = snapshot.appearance.chatBackground
        themeMode = snapshot.appearance.themeMode
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(ctx.palette().background)
            }
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

    private fun BackgroundPreview(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            SettingsCard(
                width = (ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f),
                palette = ctx::palette,
                marginTop = 12f.settingsDp(),
            ) {
                View {
                    attr {
                        height(300f.settingsDp())
                        padding(
                            top = 18f.settingsDp(),
                            left = 18f.settingsDp(),
                            right = 18f.settingsDp(),
                            bottom = 18f.settingsDp(),
                        )
                        ctx.applyPreviewGradient(this)
                    }
                    vif({ !ctx.settings.customImageUri.isNullOrBlank() }) {
                        Image {
                            attr {
                                absolutePositionAllZero()
                                resizeCover()
                                src(ctx.settings.customImageUri.orEmpty(), false)
                                touchEnable(false)
                            }
                        }
                    }
                    View {
                        attr {
                            absolutePositionAllZero()
                            backgroundColor(ctx.previewSofteningMaskColor())
                            touchEnable(false)
                        }
                    }
                    View {
                        attr {
                            absolutePositionAllZero()
                            backgroundColor(ctx.previewMaskColor())
                            touchEnable(false)
                        }
                    }
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                        }
                        Text {
                            attr {
                                text("聊天背景预览")
                                fontSize(15f.settingsDp())
                                fontWeightBold()
                                color(ctx.previewPrimaryTextColor())
                            }
                        }
                        View {
                            attr { flex(1f) }
                        }
                        View {
                            attr {
                                height(26f.settingsDp())
                                borderRadius(13f.settingsDp())
                                padding(left = 10f.settingsDp(), right = 10f.settingsDp())
                                backgroundColor(Color(0xFFFFFFFF, 0.74f))
                                allCenter()
                            }
                            Text {
                                attr {
                                    text(ctx.backgroundLabel())
                                    fontSize(10f.settingsDp())
                                    fontWeightMedium()
                                    color(Color(0xFF475569))
                                }
                            }
                        }
                    }
                    View {
                        attr {
                            alignSelfFlexEnd()
                            maxWidth(
                                (ctx.pagerData.pageViewWidth - 96f.settingsDp())
                                    .coerceAtLeast(180f.settingsDp()),
                            )
                            marginTop(34f.settingsDp())
                            padding(
                                top = 11f.settingsDp(),
                                left = 14f.settingsDp(),
                                right = 14f.settingsDp(),
                                bottom = 11f.settingsDp(),
                            )
                            borderRadius(18f.settingsDp())
                            backgroundColor(Color(0xFFFFFFFF, 0.82f))
                        }
                        Text {
                            attr {
                                text("帮我看看今天沪深 300 的表现")
                                fontSize(ctx.settings.chatTextSizeSp * SETTINGS_UI_SCALE)
                                lineHeight(ctx.settings.chatTextSizeSp * 1.45f * SETTINGS_UI_SCALE)
                                color(ctx.previewPrimaryTextColor())
                            }
                        }
                    }
                    View {
                        attr {
                            maxWidth(
                                (ctx.pagerData.pageViewWidth - 72f.settingsDp())
                                    .coerceAtLeast(210f.settingsDp()),
                            )
                            marginTop(14f.settingsDp())
                            padding(
                                top = 13f.settingsDp(),
                                left = 14f.settingsDp(),
                                right = 14f.settingsDp(),
                                bottom = 13f.settingsDp(),
                            )
                            borderRadius(18f.settingsDp())
                            backgroundColor(Color(0xFFFFFFFF, 0.9f))
                        }
                        Text {
                            attr {
                                text("沪深 300 演示行情温和上涨，关注成交量与权重板块持续性。")
                                fontSize(ctx.settings.chatTextSizeSp * SETTINGS_UI_SCALE)
                                lineHeight(ctx.settings.chatTextSizeSp * 1.48f * SETTINGS_UI_SCALE)
                                color(ctx.previewPrimaryTextColor())
                            }
                        }
                        Text {
                            attr {
                                text("+0.47%  ·  演示数据")
                                fontSize(
                                    ((ctx.settings.chatTextSizeSp - 1f).coerceAtLeast(11f)) * SETTINGS_UI_SCALE,
                                )
                                fontWeightBold()
                                color(Color(0xFFD84943))
                                marginTop(7f.settingsDp())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun BackgroundPresetPicker(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            SettingsCard(
                width = (ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f),
                palette = ctx::palette,
            ) {
                View {
                    attr {
                        padding(
                            top = 18f.settingsDp(),
                            left = 18f.settingsDp(),
                            right = 18f.settingsDp(),
                            bottom = 18f.settingsDp(),
                        )
                    }
                    Text {
                        attr {
                            text("背景方案")
                            fontSize(16f.settingsDp())
                            fontWeightBold()
                            color(ctx.palette().textPrimary)
                        }
                    }
                    Text {
                        attr {
                            text("选择一个适合阅读行情与 AI 解读的背景")
                            fontSize(12f.settingsDp())
                            color(ctx.palette().textSecondary)
                            marginTop(4f.settingsDp())
                        }
                    }
                    Scroller {
                        attr {
                            height(88f.settingsDp())
                            flexDirectionRow()
                            marginTop(14f.settingsDp())
                            showScrollerIndicator(false)
                            bouncesEnable(true)
                        }
                        BackgroundPreset.values().forEach { preset ->
                            View {
                                attr {
                                    width(92f.settingsDp())
                                    height(80f.settingsDp())
                                    marginRight(10f.settingsDp())
                                    borderRadius(15f.settingsDp())
                                    border(
                                        Border(
                                            if (ctx.isPresetSelected(preset)) {
                                                2f.settingsDp()
                                            } else {
                                                1f.settingsDp()
                                            },
                                            BorderStyle.SOLID,
                                            if (ctx.isPresetSelected(preset)) {
                                                ctx.palette().accent
                                            } else {
                                                ctx.palette().divider
                                            },
                                        ),
                                    )
                                    overflow(true)
                                }
                                event {
                                    click {
                                        ctx.updateSettings(
                                            ctx.settings.copy(
                                                preset = preset,
                                                customImageUri = null,
                                            ),
                                        )
                                    }
                                }
                                View {
                                    attr {
                                        height(49f.settingsDp())
                                        ctx.applyPresetGradient(this, preset)
                                    }
                                }
                                View {
                                    attr {
                                        height(30f.settingsDp())
                                        backgroundColor(ctx.palette().surface)
                                        allCenter()
                                    }
                                    Text {
                                        attr {
                                            text(preset.displayName)
                                            fontSize(11f.settingsDp())
                                            fontWeightMedium()
                                            color(ctx.palette().textPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    View {
                        attr {
                            height(44f.settingsDp())
                            flexDirectionRow()
                            alignItemsCenter()
                            marginTop(12f.settingsDp())
                        }
                        View {
                            attr {
                                flex(1f)
                                height(44f.settingsDp())
                                borderRadius(21f.settingsDp())
                                backgroundColor(ctx.palette().accentSoft)
                                border(Border(1f, BorderStyle.SOLID, ctx.palette().accent))
                                allCenter()
                            }
                            event {
                                click { ctx.chooseBackgroundImage() }
                            }
                            Text {
                                attr {
                                    text(if (ctx.settings.customImageUri.isNullOrBlank()) "选择本地图片" else "更换背景图片")
                                    fontSize(13f.settingsDp())
                                    fontWeightBold()
                                    color(ctx.palette().accent)
                                }
                            }
                        }
                        vif({ !ctx.settings.customImageUri.isNullOrBlank() }) {
                            View {
                                attr {
                                    width(76f.settingsDp())
                                    height(44f.settingsDp())
                                    borderRadius(21f.settingsDp())
                                    backgroundColor(ctx.palette().surfaceMuted)
                                    marginLeft(10f.settingsDp())
                                    allCenter()
                                }
                                event {
                                    click {
                                        ctx.updateSettings(ctx.settings.copy(customImageUri = null))
                                    }
                                }
                                Text {
                                    attr {
                                        text("清除")
                                        fontSize(13f.settingsDp())
                                        color(ctx.palette().textSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun EffectAdjustments(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            Text {
                attr {
                    width((ctx.pagerData.pageViewWidth - 40f.settingsDp()).coerceAtLeast(1f))
                    alignSelfCenter()
                    text("效果调整")
                    fontSize(13f.settingsDp())
                    fontWeightMedium()
                    color(ctx.palette().textTertiary)
                    marginTop(18f.settingsDp())
                    marginBottom(2f.settingsDp())
                }
            }
            SettingsCard(
                width = (ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f),
                palette = ctx::palette,
                marginTop = 8f.settingsDp(),
            ) {
                ctx.AdjustmentSlider(
                    this,
                    title = "背景柔化",
                    subtitle = "降低背景细节对股票数据阅读的干扰",
                    valueLabel = "${ctx.settings.blurRadius.roundToInt()}.00",
                    value = ctx.settings.blurRadius,
                    minimum = ChatBackgroundSettings.MIN_BLUR_RADIUS,
                    maximum = ChatBackgroundSettings.MAX_BLUR_RADIUS,
                ) { value -> ctx.updateSettings(ctx.settings.copy(blurRadius = value)) }
                SettingsDivider(ctx::palette)
                ctx.AdjustmentSlider(
                    this,
                    title = "蒙版强度",
                    subtitle = "增强统一蒙版，让页面元素更干净",
                    valueLabel = ctx.formatTwoDecimals(ctx.settings.maskOpacity),
                    value = ctx.settings.maskOpacity,
                    minimum = ChatBackgroundSettings.MIN_MASK_OPACITY,
                    maximum = ChatBackgroundSettings.MAX_MASK_OPACITY,
                ) { value -> ctx.updateSettings(ctx.settings.copy(maskOpacity = value)) }
                SettingsDivider(ctx::palette)
                ctx.AdjustmentSlider(
                    this,
                    title = "蒙版明暗",
                    subtitle = "提高或压暗蒙版，不直接修改背景方案",
                    valueLabel = ctx.formatTwoDecimals(ctx.settings.maskBrightness),
                    value = ctx.settings.maskBrightness,
                    minimum = ChatBackgroundSettings.MIN_MASK_BRIGHTNESS,
                    maximum = ChatBackgroundSettings.MAX_MASK_BRIGHTNESS,
                ) { value -> ctx.updateSettings(ctx.settings.copy(maskBrightness = value)) }
                SettingsDivider(ctx::palette)
                ctx.AdjustmentSlider(
                    this,
                    title = "聊天文本大小",
                    subtitle = "仅调整用户消息、AI 回复与思考区字号",
                    valueLabel = "${ctx.formatOneDecimal(ctx.settings.chatTextSizeSp)}sp",
                    value = ctx.settings.chatTextSizeSp,
                    minimum = ChatBackgroundSettings.MIN_CHAT_TEXT_SIZE_SP,
                    maximum = ChatBackgroundSettings.MAX_CHAT_TEXT_SIZE_SP,
                ) { value -> ctx.updateSettings(ctx.settings.copy(chatTextSizeSp = value)) }
                SettingsDivider(ctx::palette)
                ctx.TextColorPicker(this)
            }
        }
    }

    private fun AdjustmentSlider(
        container: ViewContainer<*, *>,
        title: String,
        subtitle: String,
        valueLabel: String,
        value: Float,
        minimum: Float,
        maximum: Float,
        onValueChanged: (Float) -> Unit,
    ) {
        val ctx = this
        val sliderWidth = (pagerData.pageViewWidth - 84f.settingsDp())
            .coerceAtLeast(200f.settingsDp())
        with(container) {
            View {
                attr {
                    height(132f.settingsDp())
                    padding(
                        top = 16f.settingsDp(),
                        left = 18f.settingsDp(),
                        right = 18f.settingsDp(),
                        bottom = 12f.settingsDp(),
                    )
                }
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    View {
                        attr { flex(1f) }
                        Text {
                            attr {
                                text(title)
                                fontSize(15f.settingsDp())
                                fontWeightBold()
                                color(ctx.palette().textPrimary)
                            }
                        }
                        Text {
                            attr {
                                text(subtitle)
                                fontSize(11f.settingsDp())
                                color(ctx.palette().textSecondary)
                                marginTop(4f.settingsDp())
                                lines(1)
                            }
                        }
                    }
                    Text {
                        attr {
                            text(valueLabel)
                            fontSize(12f.settingsDp())
                            color(ctx.palette().textTertiary)
                            marginLeft(12f.settingsDp())
                        }
                    }
                }
                Slider {
                    attr {
                        size(sliderWidth, 42f.settingsDp())
                        marginTop(12f.settingsDp())
                        currentProgress(((value - minimum) / (maximum - minimum)).coerceIn(0f, 1f))
                        progressColor(ctx.palette().accent)
                        trackColor(ctx.palette().divider)
                        thumbColor(ctx.palette().surface)
                        thumbSize(Size(22f.settingsDp(), 22f.settingsDp()))
                        trackThickness(4f.settingsDp())
                        padding(
                            top = 9f.settingsDp(),
                            left = 1f.settingsDp(),
                            bottom = 9f.settingsDp(),
                            right = 1f.settingsDp(),
                        )
                    }
                    event {
                        progressDidChanged { progress ->
                            onValueChanged(minimum + (maximum - minimum) * progress)
                        }
                    }
                }
            }
        }
    }

    private fun TextColorPicker(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            View {
                attr {
                    height(128f.settingsDp())
                    padding(
                        top = 16f.settingsDp(),
                        left = 18f.settingsDp(),
                        right = 18f.settingsDp(),
                        bottom = 14f.settingsDp(),
                    )
                }
                Text {
                    attr {
                        text("聊天文本颜色")
                        fontSize(15f.settingsDp())
                        fontWeightBold()
                        color(ctx.palette().textPrimary)
                    }
                }
                Text {
                    attr {
                        text("自动模式会综合背景图片与蒙版明暗选择高对比度文字")
                        fontSize(11f.settingsDp())
                        color(ctx.palette().textSecondary)
                        marginTop(4f.settingsDp())
                    }
                }
                View {
                    attr {
                        height(44f.settingsDp())
                        flexDirectionRow()
                        alignItemsCenter()
                        marginTop(12f.settingsDp())
                    }
                    ChatTextColorMode.values().forEach { mode ->
                        val selected = ctx.settings.chatTextColorMode == mode
                        View {
                            attr {
                                if (mode == ChatTextColorMode.AUTOMATIC) {
                                    height(44f.settingsDp())
                                    padding(left = 12f.settingsDp(), right = 12f.settingsDp())
                                    borderRadius(19f.settingsDp())
                                } else {
                                    size(38f.settingsDp(), 38f.settingsDp())
                                    borderRadius(19f.settingsDp())
                                }
                                marginRight(9f.settingsDp())
                                backgroundColor(ctx.textModeColor(mode))
                                border(
                                    Border(
                                        if (selected) 2f else 1f,
                                        BorderStyle.SOLID,
                                        if (selected) ctx.palette().accent else ctx.palette().divider,
                                    ),
                                )
                                allCenter()
                            }
                            event {
                                click {
                                    ctx.updateSettings(ctx.settings.copy(chatTextColorMode = mode))
                                }
                            }
                            if (mode == ChatTextColorMode.AUTOMATIC) {
                                Text {
                                    attr {
                                        text(if (selected) "✓ 自动" else "自动")
                                        fontSize(11f.settingsDp())
                                        fontWeightMedium()
                                        color(ctx.palette().textPrimary)
                                    }
                                }
                            } else if (selected) {
                                Text {
                                    attr {
                                        text("✓")
                                        fontSize(15f.settingsDp())
                                        fontWeightBold()
                                        color(if (mode == ChatTextColorMode.LIGHT) Color(0xFF374151) else Color.WHITE)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateSettings(updated: ChatBackgroundSettings) {
        settings = updated
        StockChatSettingsStore.repository.setChatBackground(updated)
    }

    private fun chooseBackgroundImage() {
        bridgeModule.pickImages(1) pickerResult@{ result ->
            if (result == null) {
                bridgeModule.toast("图片选择暂时不可用")
                return@pickerResult
            }
            if (result.optInt("cancelled", 0) == 1) {
                return@pickerResult
            }
            if (result.optInt("success", 0) != 1) {
                bridgeModule.toast(
                    result.optString("errorMessage").ifBlank { "背景图片选择失败" },
                )
                return@pickerResult
            }
            val imageUri = result.optJSONArray("previewImages")
                ?.optString(0)
                .orEmpty()
                .trim()
                .ifBlank {
                    result.optJSONArray("images")
                        ?.optString(0)
                        .orEmpty()
                        .trim()
                }
            if (imageUri.isBlank()) {
                bridgeModule.toast("没有选择可用图片")
                return@pickerResult
            }
            updateSettings(settings.copy(customImageUri = imageUri))
        }
    }

    private fun backgroundLabel(): String =
        if (settings.customImageUri.isNullOrBlank()) settings.preset.displayName else "自定义图片"

    private fun isPresetSelected(preset: BackgroundPreset): Boolean =
        settings.customImageUri.isNullOrBlank() && settings.preset == preset

    private fun closePage() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }

    private fun palette(): SettingsPalette {
        val isDark = when (themeMode) {
            ThemeMode.SYSTEM -> isNightMode()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        return if (isDark) SettingsPalettes.Dark else SettingsPalettes.Light
    }

    private fun applyPreviewGradient(attr: com.tencent.kuikly.core.base.ContainerAttr) {
        applyPresetGradient(attr, settings.preset)
    }

    private fun applyPresetGradient(
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

    private fun presetGradientArgb(preset: BackgroundPreset): Pair<Long, Long> = when (preset) {
        BackgroundPreset.DEFAULT -> 0xFFF2F5F8 to 0xFFE9EEF4
        BackgroundPreset.MARKET_BLUE -> 0xFFDDEBFF to 0xFFBFD8F7
        BackgroundPreset.GRAPHITE -> 0xFF394253 to 0xFF1C2532
        BackgroundPreset.FOREST -> 0xFFBDE0D0 to 0xFF729E8B
        BackgroundPreset.SUNSET -> 0xFFFFE0BE to 0xFFF2A98E
    }

    private fun previewMaskColor(): Color {
        val contrast = previewContrast()
        return Color(contrast.maskColorArgb, contrast.maskAlpha)
    }

    private fun previewSofteningMaskColor(): Color {
        val alpha = (
            settings.blurRadius / ChatBackgroundSettings.MAX_BLUR_RADIUS * 0.12f
        ).coerceIn(0f, 0.12f)
        return if (settings.preset == BackgroundPreset.GRAPHITE) {
            Color(0xFF000000, alpha)
        } else {
            Color(0xFFFFFFFF, alpha)
        }
    }

    private fun previewPrimaryTextColor(): Color = when (settings.chatTextColorMode) {
        ChatTextColorMode.AUTOMATIC -> Color(previewContrast().textColorArgb)
        ChatTextColorMode.LIGHT -> Color.WHITE
        ChatTextColorMode.DARK -> Color(0xFF222831)
        ChatTextColorMode.BLUE -> Color(0xFF1F4B86)
        ChatTextColorMode.GREEN -> Color(0xFF176D57)
        ChatTextColorMode.ORANGE -> Color(0xFFB96016)
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

    private fun textModeColor(mode: ChatTextColorMode): Color = when (mode) {
        ChatTextColorMode.AUTOMATIC -> palette().surfaceMuted
        ChatTextColorMode.LIGHT -> Color.WHITE
        ChatTextColorMode.DARK -> Color(0xFF2A3445)
        ChatTextColorMode.BLUE -> Color(0xFF3D6FB4)
        ChatTextColorMode.GREEN -> Color(0xFF2D8B68)
        ChatTextColorMode.ORANGE -> Color(0xFFF39A18)
    }

    private fun formatTwoDecimals(value: Float): String =
        ((value * 100f).roundToInt() / 100f).toString()

    private fun formatOneDecimal(value: Float): String =
        ((value * 10f).roundToInt() / 10f).toString()
}
