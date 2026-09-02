package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklytableview.table.TableBorderOptions
import com.guet.liang.kuiklytableview.table.TableBorderPreset
import com.guet.liang.kuiklytableview.table.TableHeaderStyle
import com.guet.liang.kuiklytableview.table.TableStyleOptions
import com.guet.liang.kuiklytableview.table.TableStylePreset as KuiklyTableStylePreset
import com.guet.liang.stockchat.model.TableStylePreset
import com.guet.liang.stockchat.model.TableStyleSettings

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
            presetOptions.headerBackgroundColor
        } else {
            presetOptions.rowBackgroundColor
        },
        headerTextColor = if (highlightHeader) {
            presetOptions.headerTextColor
        } else {
            presetOptions.textColor
        },
    )
}

private fun TableStylePreset.toKuiklyTableStylePreset(): KuiklyTableStylePreset = when (this) {
    TableStylePreset.DEFAULT -> KuiklyTableStylePreset.Default
    TableStylePreset.COMPACT -> KuiklyTableStylePreset.Compact
    TableStylePreset.SPACIOUS -> KuiklyTableStylePreset.Spacious
    TableStylePreset.MINIMAL -> KuiklyTableStylePreset.Minimal
    TableStylePreset.BLUE -> KuiklyTableStylePreset.Blue
    TableStylePreset.DARK -> KuiklyTableStylePreset.Dark
}
