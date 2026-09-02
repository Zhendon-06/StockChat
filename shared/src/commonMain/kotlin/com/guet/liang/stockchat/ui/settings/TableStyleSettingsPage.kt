package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.model.TableStylePreset
import com.guet.liang.stockchat.model.TableStyleSettings
import com.guet.liang.stockchat.model.ThemeMode
import com.guet.liang.stockchat.ui.StockTableStyleChoice
import com.guet.liang.stockchat.ui.StockTableStylePreview
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.base.event.PanGestureParams
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.abs
import kotlin.math.roundToInt

@Page(TABLE_STYLE_SETTINGS_PAGE_NAME, supportInLocal = true)
internal class TableStyleSettingsPage : BasePager() {
    private var themeMode by observable(ThemeMode.SYSTEM)
    private var savedSettings = TableStyleSettings()
    private var selectedPreset by observable(TableStylePreset.DEFAULT)
    private var selectedCustomColorArgb by observable(TableStyleSettings.DEFAULT_CUSTOM_COLOR_ARGB)
    private var selectedHue by observable(DEFAULT_HUE)
    private var selectedSaturation by observable(DEFAULT_SATURATION)
    private var selectedBrightness by observable(DEFAULT_BRIGHTNESS)
    private var previewRefreshKey by observable(0)

    override fun created() {
        super.created()
        val appearance = StockChatSettingsStore.repository.loadSnapshot().appearance
        themeMode = appearance.themeMode
        savedSettings = appearance.tableStyle
        selectedPreset = when (appearance.tableStyle.preset) {
            TableStylePreset.BLUE,
            TableStylePreset.DARK,
            -> TableStylePreset.DEFAULT
            else -> appearance.tableStyle.preset
        }
        selectedCustomColorArgb = appearance.tableStyle.customColorArgb
        val hsv = argbToHsv(selectedCustomColorArgb)
        selectedHue = hsv.hue
        selectedSaturation = hsv.saturation
        selectedBrightness = hsv.brightness
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(ctx.palette().background)
            }
            SettingsPageHeader(
                statusBarHeight = ctx.pagerData.statusBarHeight,
                title = "表格样式",
                palette = { ctx.palette() },
                onBack = { ctx.closePage() },
            )
            Scroller {
                attr {
                    absolutePosition(
                        top = ctx.pagerData.statusBarHeight + SETTINGS_HEADER_HEIGHT,
                        left = 0f,
                        right = 0f,
                        bottom = ctx.pagerData.safeAreaInsets.bottom,
                    )
                    showScrollerIndicator(false)
                    bouncesEnable(true)
                }
                ctx.TablePreview(this)
                ctx.StyleChoices(this)
                ctx.ApplyButton(this)
                Text {
                    attr {
                        width(settingsContentWidth(ctx.pagerData.pageViewWidth, PAGE_HORIZONTAL_MARGIN))
                        alignSelfCenter()
                        text("表格内均为演示行情 · 仅供参考，不构成投资建议")
                        fontSize(12f.settingsDp())
                        lineHeight(18f.settingsDp())
                        textAlignCenter()
                        color(ctx.palette().textTertiary)
                        marginTop(12f.settingsDp())
                    }
                }
                View {
                    attr {
                        height(28f.settingsDp())
                    }
                }
            }
        }
    }

    private fun TablePreview(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            SettingsCard(
                width = settingsContentWidth(ctx.pagerData.pageViewWidth, PAGE_HORIZONTAL_MARGIN),
                palette = { ctx.palette() },
                marginTop = 14f.settingsDp(),
            ) {
                View {
                    attr {
                        padding(
                            top = 18f.settingsDp(),
                            left = 16f.settingsDp(),
                            right = 16f.settingsDp(),
                            bottom = 18f.settingsDp(),
                        )
                    }
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            justifyContentSpaceBetween()
                        }
                        View {
                            attr {
                                flex(1f)
                                marginRight(12f.settingsDp())
                            }
                            Text {
                                attr {
                                    text("股票行情预览")
                                    fontSize(18f.settingsDp())
                                    fontWeightBold()
                                    color(ctx.palette().textPrimary)
                                }
                            }
                            Text {
                                attr {
                                    text("左右滑动可查看完整字段")
                                    fontSize(12f.settingsDp())
                                    color(ctx.palette().textSecondary)
                                    marginTop(4f.settingsDp())
                                }
                            }
                        }
                        View {
                            attr {
                                borderRadius(12f.settingsDp())
                                backgroundColor(ctx.palette().accentSoft)
                                padding(
                                    top = 7f.settingsDp(),
                                    left = 10f.settingsDp(),
                                    right = 10f.settingsDp(),
                                    bottom = 7f.settingsDp(),
                                )
                            }
                            Text {
                                attr {
                                    text(ctx.selectedPreset.displayName)
                                    fontSize(12f.settingsDp())
                                    fontWeightBold()
                                    color(ctx.palette().accent)
                                }
                            }
                        }
                    }
                    View {
                        attr {
                            height(TABLE_PREVIEW_HEIGHT)
                            marginTop(16f.settingsDp())
                            borderRadius(14f.settingsDp())
                            overflow(true)
                            backgroundColor(ctx.palette().surfaceMuted)
                        }
                        StockTableStylePreview(
                            selectedStyle = { ctx.previewChoice(ctx.selectedPreset) },
                            viewportHeight = TABLE_PREVIEW_HEIGHT,
                            uiScale = SETTINGS_UI_SCALE,
                            customColor = { Color(ctx.selectedCustomColorArgb) },
                            refreshKey = { ctx.previewRefreshKey },
                        )
                    }
                }
            }
        }
    }

    private fun StyleChoices(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            SettingsCard(
                width = settingsContentWidth(ctx.pagerData.pageViewWidth, PAGE_HORIZONTAL_MARGIN),
                palette = { ctx.palette() },
            ) {
                View {
                    attr {
                        padding(
                            top = 18f.settingsDp(),
                            left = 20f.settingsDp(),
                            right = 20f.settingsDp(),
                            bottom = 10f.settingsDp(),
                        )
                    }
                    Text {
                        attr {
                            text("选择样式")
                            fontSize(17f.settingsDp())
                            fontWeightBold()
                            color(ctx.palette().textPrimary)
                        }
                    }
                    Text {
                        attr {
                            text("样式会应用到聊天中的股票表格与对比结果。")
                            fontSize(12f.settingsDp())
                            lineHeight(18f.settingsDp())
                            color(ctx.palette().textSecondary)
                            marginTop(4f.settingsDp())
                        }
                    }
                }
                StockTableStyleChoice.all.forEachIndexed { index, choice ->
                    ctx.StyleChoiceRow(this, choice)
                    if (index < StockTableStyleChoice.all.lastIndex) {
                        SettingsDivider(
                            palette = { ctx.palette() },
                            inset = 84f.settingsDp(),
                        )
                    }
                }
            }
            ctx.ColorPalette(this)
        }
    }

    private fun ColorPalette(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            SettingsCard(
                width = settingsContentWidth(ctx.pagerData.pageViewWidth, PAGE_HORIZONTAL_MARGIN),
                palette = { ctx.palette() },
            ) {
                View {
                    attr {
                        padding(
                            top = 18f.settingsDp(),
                            left = 20f.settingsDp(),
                            right = 20f.settingsDp(),
                            bottom = 16f.settingsDp(),
                        )
                    }
                    Text {
                        attr {
                            text("自定义配色")
                            fontSize(17f.settingsDp())
                            fontWeightBold()
                            color(ctx.palette().textPrimary)
                        }
                    }
                    Text {
                        attr {
                            text("从调色盘中选择表头强调色，实时应用到表格预览。")
                            fontSize(12f.settingsDp())
                            lineHeight(18f.settingsDp())
                            color(ctx.palette().textSecondary)
                            marginTop(4f.settingsDp())
                        }
                    }
                    ctx.ColorField(this)
                    ctx.HueSlider(this)
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            marginTop(14f.settingsDp())
                        }
                        Text {
                            attr {
                                text("当前颜色")
                                fontSize(12f.settingsDp())
                                color(ctx.palette().textSecondary)
                            }
                        }
                        View {
                            attr {
                                size(16f.settingsDp(), 16f.settingsDp())
                                borderRadius(8f.settingsDp())
                                marginLeft(8f.settingsDp())
                                backgroundColor(Color(ctx.selectedCustomColorArgb))
                                border(Border(1f, BorderStyle.SOLID, ctx.palette().divider))
                            }
                        }
                        Text {
                            attr {
                                text(ctx.formatHexColor(ctx.selectedCustomColorArgb))
                                fontSize(12f.settingsDp())
                                color(ctx.palette().textPrimary)
                                marginLeft(6f.settingsDp())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun ColorField(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            View {
                attr {
                    width(settingsContentWidth(ctx.pagerData.pageViewWidth, PAGE_HORIZONTAL_MARGIN) - 40f.settingsDp())
                    height(COLOR_FIELD_HEIGHT)
                    marginTop(16f.settingsDp())
                    borderRadius(14f.settingsDp())
                    overflow(true)
                    capture(CaptureRule.pan(CaptureRuleDirection.ALL))
                }
                event {
                    click { params -> ctx.selectColorField(params.x, params.y) }
                    pan { params -> ctx.handleColorFieldPan(params) }
                }
                Canvas({
                    attr {
                        absolutePositionAllZero()
                    }
                }) { canvas, width, height ->
                    ctx.drawColorField(canvas, width, height)
                }
            }
        }
    }

    private fun HueSlider(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            View {
                attr {
                    width(settingsContentWidth(ctx.pagerData.pageViewWidth, PAGE_HORIZONTAL_MARGIN) - 40f.settingsDp())
                    height(COLOR_HUE_HEIGHT)
                    marginTop(12f.settingsDp())
                    borderRadius(9f.settingsDp())
                    overflow(true)
                    capture(CaptureRule.pan(CaptureRuleDirection.ALL))
                }
                event {
                    click { params -> ctx.selectHue(params.x) }
                    pan { params -> ctx.handleHuePan(params) }
                }
                Canvas({
                    attr {
                        absolutePositionAllZero()
                    }
                }) { canvas, width, height ->
                    ctx.drawHueSlider(canvas, width, height)
                }
            }
        }
    }

    private fun handleColorFieldPan(params: PanGestureParams) {
        if (params.state == "start" || params.state == "move" || params.state == "end") {
            selectColorField(params.x, params.y)
        }
    }

    private fun handleHuePan(params: PanGestureParams) {
        if (params.state == "start" || params.state == "move" || params.state == "end") {
            selectHue(params.x)
        }
    }

    private fun selectColorField(x: Float, y: Float) {
        val width = colorPickerContentWidth()
        val saturation = (x / width).coerceIn(0f, 1f)
        val brightness = (1f - y / COLOR_FIELD_HEIGHT).coerceIn(0f, 1f)
        updateSelectedColor(selectedHue, saturation, brightness)
    }

    private fun selectHue(x: Float) {
        val width = colorPickerContentWidth()
        val hue = (x / width).coerceIn(0f, 1f) * HUE_MAX
        updateSelectedColor(hue, selectedSaturation, selectedBrightness)
    }

    private fun updateSelectedColor(hue: Float, saturation: Float, brightness: Float) {
        selectedHue = hue.coerceIn(0f, HUE_MAX)
        selectedSaturation = saturation.coerceIn(0f, 1f)
        selectedBrightness = brightness.coerceIn(0f, 1f)
        val colorArgb = hsvToArgb(selectedHue, selectedSaturation, selectedBrightness)
        if (colorArgb != selectedCustomColorArgb) {
            selectedCustomColorArgb = colorArgb
            previewRefreshKey += 1
        }
    }

    private fun drawColorField(canvas: CanvasContext, width: Float, height: Float) {
        val hueColor = Color(hsvToArgb(selectedHue, 1f, 1f))
        val saturationGradient = canvas.createLinearGradient(0f, 0f, width, 0f).apply {
            addColorStop(0f, Color.WHITE)
            addColorStop(1f, hueColor)
        }
        canvas.fillStyle(saturationGradient)
        fillRect(canvas, width, height)

        val brightnessGradient = canvas.createLinearGradient(0f, 0f, 0f, height).apply {
            addColorStop(0f, Color.TRANSPARENT)
            addColorStop(1f, Color.BLACK)
        }
        canvas.fillStyle(brightnessGradient)
        fillRect(canvas, width, height)

        val cursorX = selectedSaturation * width
        val cursorY = (1f - selectedBrightness) * height
        canvas.beginPath()
        canvas.arc(cursorX, cursorY, COLOR_CURSOR_RADIUS + 2f, 0f, TWO_PI, false)
        canvas.lineWidth(3f)
        canvas.strokeStyle(Color.WHITE)
        canvas.stroke()
        canvas.beginPath()
        canvas.arc(cursorX, cursorY, COLOR_CURSOR_RADIUS, 0f, TWO_PI, false)
        canvas.lineWidth(1f)
        canvas.strokeStyle(Color(0x66000000L))
        canvas.stroke()
    }

    private fun drawHueSlider(canvas: CanvasContext, width: Float, height: Float) {
        val hueGradient = canvas.createLinearGradient(0f, 0f, width, 0f).apply {
            HUE_COLORS.forEachIndexed { index, color ->
                addColorStop(index.toFloat() / (HUE_COLORS.lastIndex).toFloat(), Color(color))
            }
        }
        canvas.fillStyle(hueGradient)
        fillRect(canvas, width, height)

        val cursorX = selectedHue / HUE_MAX * width
        canvas.beginPath()
        canvas.arc(cursorX, height / 2f, COLOR_HUE_CURSOR_RADIUS + 2f, 0f, TWO_PI, false)
        canvas.lineWidth(3f)
        canvas.strokeStyle(Color.WHITE)
        canvas.stroke()
        canvas.beginPath()
        canvas.arc(cursorX, height / 2f, COLOR_HUE_CURSOR_RADIUS, 0f, TWO_PI, false)
        canvas.lineWidth(1f)
        canvas.strokeStyle(Color(0x66000000L))
        canvas.stroke()
    }

    private fun fillRect(canvas: CanvasContext, width: Float, height: Float) {
        canvas.beginPath()
        canvas.moveTo(0f, 0f)
        canvas.lineTo(width, 0f)
        canvas.lineTo(width, height)
        canvas.lineTo(0f, height)
        canvas.closePath()
        canvas.fill()
    }

    private fun colorPickerContentWidth(): Float {
        return (settingsContentWidth(pagerData.pageViewWidth, PAGE_HORIZONTAL_MARGIN) - 40f.settingsDp())
            .coerceAtLeast(1f)
    }

    private fun StyleChoiceRow(
        container: ViewContainer<*, *>,
        choice: StockTableStyleChoice,
    ) {
        val ctx = this
        with(container) {
            View {
                attr {
                    minHeight(74f.settingsDp())
                    padding(
                        top = 10f.settingsDp(),
                        left = 18f.settingsDp(),
                        right = 18f.settingsDp(),
                        bottom = 10f.settingsDp(),
                    )
                    flexDirectionRow()
                    alignItemsCenter()
                    backgroundColor(
                        if (ctx.isSelected(choice)) {
                            ctx.palette().accentSoft
                        } else {
                            ctx.palette().surface
                        },
                    )
                }
                event {
                    click {
                        ctx.selectedPreset = ctx.settingsPreset(choice)
                    }
                }
                View {
                    attr {
                        size(48f.settingsDp(), 48f.settingsDp())
                        borderRadius(13f.settingsDp())
                        backgroundColor(
                            if (ctx.isSelected(choice)) {
                                ctx.palette().surface
                            } else {
                                ctx.palette().surfaceMuted
                            },
                        )
                        allCenter()
                    }
                    repeat(3) { lineIndex ->
                        View {
                            attr {
                                width(27f.settingsDp())
                                height(if (lineIndex == 0) 5f.settingsDp() else 3f.settingsDp())
                                borderRadius(2f.settingsDp())
                                backgroundColor(ctx.choiceAccent(choice))
                                if (lineIndex > 0) {
                                    marginTop(4f.settingsDp())
                                }
                            }
                        }
                    }
                }
                View {
                    attr {
                        flex(1f)
                        marginLeft(14f.settingsDp())
                        marginRight(12f.settingsDp())
                    }
                    Text {
                        attr {
                            text(choice.title)
                            fontSize(16f.settingsDp())
                            fontWeightBold()
                            color(ctx.palette().textPrimary)
                        }
                    }
                    Text {
                        attr {
                            text(choice.description)
                            fontSize(12f.settingsDp())
                            lineHeight(18f.settingsDp())
                            color(ctx.palette().textSecondary)
                            marginTop(3f.settingsDp())
                        }
                    }
                }
                View {
                    attr {
                        size(24f.settingsDp(), 24f.settingsDp())
                        borderRadius(12f.settingsDp())
                        backgroundColor(
                            if (ctx.isSelected(choice)) {
                                ctx.palette().accent
                            } else {
                                ctx.palette().surfaceMuted
                            },
                        )
                        allCenter()
                    }
                    Text {
                        attr {
                            text(if (ctx.isSelected(choice)) "✓" else "")
                            fontSize(14f.settingsDp())
                            fontWeightBold()
                            color(Color.WHITE)
                            marginBottom(1f.settingsDp())
                        }
                    }
                }
            }
        }
    }

    private fun ApplyButton(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            View {
                attr {
                    width(settingsContentWidth(ctx.pagerData.pageViewWidth, PAGE_HORIZONTAL_MARGIN))
                    height(50f.settingsDp())
                    alignSelfCenter()
                    marginTop(18f.settingsDp())
                    borderRadius(25f.settingsDp())
                    backgroundColor(ctx.palette().accent)
                    allCenter()
                }
                event {
                    click { ctx.applyAndClose() }
                }
                Text {
                    attr {
                        text("应用此样式")
                        fontSize(16f.settingsDp())
                        fontWeightBold()
                        color(Color.WHITE)
                    }
                }
            }
        }
    }

    private fun isSelected(choice: StockTableStyleChoice): Boolean {
        return selectedPreset == settingsPreset(choice)
    }

    private fun choiceAccent(choice: StockTableStyleChoice): Color {
        return if (isSelected(choice)) Color(selectedCustomColorArgb) else palette().textTertiary
    }

    private fun applyAndClose() {
        StockChatSettingsStore.repository.setTableStyle(
            savedSettings.copy(
                preset = selectedPreset,
                customColorArgb = selectedCustomColorArgb,
            ),
        )
        closePage()
    }

    private fun palette(): SettingsPalette {
        val isDark = when (themeMode) {
            ThemeMode.SYSTEM -> isNightMode()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        return if (isDark) SettingsPalettes.Dark else SettingsPalettes.Light
    }

    private fun closePage() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }

    private fun previewChoice(preset: TableStylePreset): StockTableStyleChoice {
        return when (preset) {
            TableStylePreset.DEFAULT -> StockTableStyleChoice.DEFAULT
            TableStylePreset.COMPACT -> StockTableStyleChoice.COMPACT
            TableStylePreset.SPACIOUS -> StockTableStyleChoice.SPACIOUS
            TableStylePreset.MINIMAL -> StockTableStyleChoice.MINIMAL
            TableStylePreset.BLUE,
            TableStylePreset.DARK,
            -> StockTableStyleChoice.DEFAULT
        }
    }

    private fun settingsPreset(choice: StockTableStyleChoice): TableStylePreset {
        return when (choice) {
            StockTableStyleChoice.DEFAULT -> TableStylePreset.DEFAULT
            StockTableStyleChoice.COMPACT -> TableStylePreset.COMPACT
            StockTableStyleChoice.SPACIOUS -> TableStylePreset.SPACIOUS
            StockTableStyleChoice.MINIMAL -> TableStylePreset.MINIMAL
        }
    }

    private fun formatHexColor(colorArgb: Long): String {
        return "#${colorArgb.toString(16).takeLast(6).padStart(6, '0').uppercase()}"
    }

    private fun isLightColor(colorArgb: Long): Boolean {
        val red = (colorArgb shr 16 and 0xFF).toInt()
        val green = (colorArgb shr 8 and 0xFF).toInt()
        val blue = (colorArgb and 0xFF).toInt()
        return red * 299 + green * 587 + blue * 114 >= 150_000
    }

    private fun hsvToArgb(hue: Float, saturation: Float, brightness: Float): Long {
        val normalizedHue = ((hue % HUE_MAX) + HUE_MAX) % HUE_MAX
        val safeSaturation = saturation.coerceIn(0f, 1f)
        val safeBrightness = brightness.coerceIn(0f, 1f)
        val chroma = safeBrightness * safeSaturation
        val hueSector = normalizedHue / 60f
        val secondComponent = chroma * (1f - abs(hueSector % 2f - 1f))
        val rgb = when {
            hueSector < 1f -> Triple(chroma, secondComponent, 0f)
            hueSector < 2f -> Triple(secondComponent, chroma, 0f)
            hueSector < 3f -> Triple(0f, chroma, secondComponent)
            hueSector < 4f -> Triple(0f, secondComponent, chroma)
            hueSector < 5f -> Triple(secondComponent, 0f, chroma)
            else -> Triple(chroma, 0f, secondComponent)
        }
        val match = safeBrightness - chroma
        val red = ((rgb.first + match) * 255f).roundToInt().coerceIn(0, 255).toLong()
        val green = ((rgb.second + match) * 255f).roundToInt().coerceIn(0, 255).toLong()
        val blue = ((rgb.third + match) * 255f).roundToInt().coerceIn(0, 255).toLong()
        return 0xFF000000L or (red shl 16) or (green shl 8) or blue
    }

    private fun argbToHsv(colorArgb: Long): HsvColor {
        val red = ((colorArgb shr 16) and 0xFF).toFloat() / 255f
        val green = ((colorArgb shr 8) and 0xFF).toFloat() / 255f
        val blue = (colorArgb and 0xFF).toFloat() / 255f
        val maximum = maxOf(red, green, blue)
        val minimum = minOf(red, green, blue)
        val delta = maximum - minimum
        val hue = when {
            delta == 0f -> 0f
            maximum == red -> (60f * ((green - blue) / delta) + HUE_MAX) % HUE_MAX
            maximum == green -> 60f * ((blue - red) / delta + 2f)
            else -> 60f * ((red - green) / delta + 4f)
        }
        val saturation = if (maximum == 0f) 0f else delta / maximum
        return HsvColor(hue, saturation, maximum)
    }

    private data class HsvColor(
        val hue: Float,
        val saturation: Float,
        val brightness: Float,
    )

    private companion object {
        const val PAGE_HORIZONTAL_MARGIN = 16f
        val TABLE_PREVIEW_HEIGHT = 232f.settingsDp()
        val COLOR_FIELD_HEIGHT = 160f.settingsDp()
        val COLOR_HUE_HEIGHT = 24f.settingsDp()
        const val COLOR_CURSOR_RADIUS = 7f
        const val COLOR_HUE_CURSOR_RADIUS = 8f
        const val DEFAULT_HUE = 155f
        const val DEFAULT_SATURATION = 0.92f
        const val DEFAULT_BRIGHTNESS = 0.67f
        const val HUE_MAX = 360f
        const val TWO_PI = 6.2831855f
        val HUE_COLORS = listOf(
            0xFFFF0000L,
            0xFFFFFF00L,
            0xFF00FF00L,
            0xFF00FFFFL,
            0xFF0000FFL,
            0xFFFF00FFL,
            0xFFFF0000L,
        )
    }
}
