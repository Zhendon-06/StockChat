package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklytableview.table.TableBorderOptions
import com.guet.liang.kuiklytableview.table.TableBorderPreset
import com.guet.liang.kuiklytableview.table.TableHeaderStyle
import com.guet.liang.kuiklytableview.table.TableStyleOptions
import com.guet.liang.kuiklytableview.table.TableStylePreset as KuiklyTableStylePreset
import com.guet.liang.stockchat.model.TableStylePreset
import com.guet.liang.stockchat.model.TableStyleSettings
import com.tencent.kuikly.core.base.Color

internal fun TableStyleSettings.toKuiklyTableStyleOptions(): TableStyleOptions {
    val presetOptions = TableStyleOptions.preset(preset.toKuiklyTableStylePreset())
    return presetOptions.copy(
        borders = if (showGridLines) {
            presetOptions.borders
        } else {
            TableBorderOptions.forPreset(TableBorderPreset.None)
        },
        headerStyle = if (highlightHeader) presetOptions.headerStyle else TableHeaderStyle.Plain,
        headerBackgroundColor = if (highlightHeader) {
            Color(customColorArgb)
        } else {
            presetOptions.rowBackgroundColor
        },
        headerTextColor = if (highlightHeader) {
            if (customColorArgb.isLightColor()) Color(0xFF1D2027) else Color.WHITE
        } else {
            presetOptions.textColor
        },
    )
}

private fun Long.isLightColor(): Boolean {
    val red = (this shr 16 and 0xFF).toInt()
    val green = (this shr 8 and 0xFF).toInt()
    val blue = (this and 0xFF).toInt()
    return red * 299 + green * 587 + blue * 114 >= 150_000
}

private fun TableStylePreset.toKuiklyTableStylePreset(): KuiklyTableStylePreset = when (this) {
    TableStylePreset.DEFAULT -> KuiklyTableStylePreset.Default
    TableStylePreset.COMPACT -> KuiklyTableStylePreset.Compact
    TableStylePreset.SPACIOUS -> KuiklyTableStylePreset.Spacious
    TableStylePreset.MINIMAL -> KuiklyTableStylePreset.Minimal
    TableStylePreset.BLUE -> KuiklyTableStylePreset.Blue
    TableStylePreset.DARK -> KuiklyTableStylePreset.Dark
}
