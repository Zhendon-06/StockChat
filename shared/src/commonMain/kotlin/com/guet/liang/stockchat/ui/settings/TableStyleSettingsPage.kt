package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.closePage
import com.guet.liang.stockchat.controller.SettingsController
import com.guet.liang.stockchat.controller.settingsController
import com.guet.liang.stockchat.model.TableStylePreset
import com.guet.liang.stockchat.model.TableStyleSettings
import com.guet.liang.stockchat.model.ThemeMode
import com.guet.liang.stockchat.ui.StockChatTheme
import com.guet.liang.stockchat.ui.StockTableStyleChoice
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.event.PanGestureParams
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.abs
import kotlin.math.roundToInt

@Page(TABLE_STYLE_SETTINGS_PAGE_NAME, supportInLocal = true)
/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal class TableStyleSettingsPage : BasePager() {
    private lateinit var controller: SettingsController
    internal var themeMode by observable(ThemeMode.SYSTEM)
    internal var savedSettings = TableStyleSettings()
    internal var selectedPreset by observable(TableStylePreset.DEFAULT)
    internal var selectedCustomColorArgb by observable(TableStyleSettings.DEFAULT_CUSTOM_COLOR_ARGB)
    internal var selectedHue by observable(DEFAULT_HUE)
    internal var selectedSaturation by observable(DEFAULT_SATURATION)
    internal var selectedBrightness by observable(DEFAULT_BRIGHTNESS)
    internal var previewRefreshKey by observable(0)

    override fun created() {
        super.created()
        controller = settingsController()
        val appearance = controller.snapshot().appearance
        themeMode = appearance.themeMode
        savedSettings = appearance.tableStyle
        selectedPreset =
            when (appearance.tableStyle.preset) {
                TableStylePreset.BLUE,
                TableStylePreset.DARK -> TableStylePreset.DEFAULT
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
            attr { backgroundColor(ctx.palette().background) }
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
                        width(
                            settingsContentWidth(
                                ctx.pagerData.pageViewWidth,
                                PAGE_HORIZONTAL_MARGIN,
                            )
                        )
                        alignSelfCenter()
                        text("表格内均为演示行情 · 仅供参考，不构成投资建议")
                        fontSize(12f.settingsDp())
                        lineHeight(18f.settingsDp())
                        textAlignCenter()
                        color(ctx.palette().textTertiary)
                        marginTop(12f.settingsDp())
                    }
                }
                View { attr { height(28f.settingsDp()) } }
            }
        }
    }

    internal fun handleColorFieldPan(params: PanGestureParams) {
        if (params.state == "start" || params.state == "move" || params.state == "end") {
            selectColorField(params.x, params.y)
        }
    }

    internal fun handleHuePan(params: PanGestureParams) {
        if (params.state == "start" || params.state == "move" || params.state == "end") {
            selectHue(params.x)
        }
    }

    internal fun selectColorField(x: Float, y: Float) {
        val width = colorPickerContentWidth()
        val saturation = (x / width).coerceIn(0f, 1f)
        val brightness = (1f - y / COLOR_FIELD_HEIGHT).coerceIn(0f, 1f)
        updateSelectedColor(selectedHue, saturation, brightness)
    }

    internal fun selectHue(x: Float) {
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

    internal fun drawColorField(canvas: CanvasContext, width: Float, height: Float) {
        val hueColor = Color(hsvToArgb(selectedHue, 1f, 1f))
        val saturationGradient =
            canvas.createLinearGradient(0f, 0f, width, 0f).apply {
                addColorStop(0f, Color.WHITE)
                addColorStop(1f, hueColor)
            }
        canvas.fillStyle(saturationGradient)
        fillRect(canvas, width, height)

        val brightnessGradient =
            canvas.createLinearGradient(0f, 0f, 0f, height).apply {
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

    internal fun drawHueSlider(canvas: CanvasContext, width: Float, height: Float) {
        val hueGradient =
            canvas.createLinearGradient(0f, 0f, width, 0f).apply {
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
        return (settingsContentWidth(pagerData.pageViewWidth, PAGE_HORIZONTAL_MARGIN) -
                40f.settingsDp())
            .coerceAtLeast(1f)
    }

    internal fun isSelected(choice: StockTableStyleChoice): Boolean {
        return selectedPreset == settingsPreset(choice)
    }

    internal fun choiceAccent(choice: StockTableStyleChoice): Color {
        return if (isSelected(choice)) Color(selectedCustomColorArgb) else palette().textTertiary
    }

    internal fun applyAndClose() {
        controller.setTableStyle(
            savedSettings.copy(preset = selectedPreset, customColorArgb = selectedCustomColorArgb)
        )
        closePage()
    }

    internal fun palette(): SettingsPalette = settingsPalette(themeMode)

    internal fun previewChoice(preset: TableStylePreset): StockTableStyleChoice {
        return when (preset) {
            TableStylePreset.DEFAULT -> StockTableStyleChoice.DEFAULT
            TableStylePreset.COMPACT -> StockTableStyleChoice.COMPACT
            TableStylePreset.SPACIOUS -> StockTableStyleChoice.SPACIOUS
            TableStylePreset.MINIMAL -> StockTableStyleChoice.MINIMAL
            TableStylePreset.BLUE,
            TableStylePreset.DARK -> StockTableStyleChoice.DEFAULT
        }
    }

    internal fun settingsPreset(choice: StockTableStyleChoice): TableStylePreset {
        return when (choice) {
            StockTableStyleChoice.DEFAULT -> TableStylePreset.DEFAULT
            StockTableStyleChoice.COMPACT -> TableStylePreset.COMPACT
            StockTableStyleChoice.SPACIOUS -> TableStylePreset.SPACIOUS
            StockTableStyleChoice.MINIMAL -> TableStylePreset.MINIMAL
        }
    }

    internal fun formatHexColor(colorArgb: Long): String {
        return "#${colorArgb.toString(16).takeLast(6).padStart(6, '0').uppercase()}"
    }

    private fun hsvToArgb(hue: Float, saturation: Float, brightness: Float): Long {
        val normalizedHue = ((hue % HUE_MAX) + HUE_MAX) % HUE_MAX
        val safeSaturation = saturation.coerceIn(0f, 1f)
        val safeBrightness = brightness.coerceIn(0f, 1f)
        val chroma = safeBrightness * safeSaturation
        val hueSector = normalizedHue / 60f
        val secondComponent = chroma * (1f - abs(hueSector % 2f - 1f))
        val rgb =
            when {
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
        return StockChatTheme.COLOR_FF000000L or (red shl 16) or (green shl 8) or blue
    }

    private fun argbToHsv(colorArgb: Long): HsvColor {
        val red = ((colorArgb shr 16) and 0xFF).toFloat() / 255f
        val green = ((colorArgb shr 8) and 0xFF).toFloat() / 255f
        val blue = (colorArgb and 0xFF).toFloat() / 255f
        val maximum = maxOf(red, green, blue)
        val minimum = minOf(red, green, blue)
        val delta = maximum - minimum
        val hue =
            when {
                delta == 0f -> 0f
                maximum == red -> (60f * ((green - blue) / delta) + HUE_MAX) % HUE_MAX
                maximum == green -> 60f * ((blue - red) / delta + 2f)
                else -> 60f * ((red - green) / delta + 4f)
            }
        val saturation = if (maximum == 0f) 0f else delta / maximum
        return HsvColor(hue, saturation, maximum)
    }

    private data class HsvColor(val hue: Float, val saturation: Float, val brightness: Float)

    companion object {
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
        val HUE_COLORS =
            listOf(
                StockChatTheme.COLOR_FFFF0000L,
                StockChatTheme.COLOR_FFFFFF00L,
                StockChatTheme.COLOR_FF00FF00L,
                StockChatTheme.COLOR_FF00FFFFL,
                StockChatTheme.COLOR_FF0000FFL,
                StockChatTheme.COLOR_FFFF00FFL,
                StockChatTheme.COLOR_FFFF0000L,
            )
    }
}
