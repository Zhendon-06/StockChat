package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklychart.finance.financialNumber
import com.guet.liang.kuiklychart.finance.financialVolume
import com.tencent.kuikly.core.base.Color

internal fun flowColor(value: Float): Color = if (value >= 0) StockChatTheme.positive else StockChatTheme.negative
internal fun signed(value: Float): String = (if (value > 0) "+" else "") + financialNumber(value)
internal fun signedAmount(value: Float): String = (if (value > 0) "+" else "") + financialVolume(value)
internal fun unit(value: String, suffix: String): String = if (value.isBlank() || value == "-") "--" else value + suffix
internal fun percent(value: String): String = unit(value, "%")
internal fun quantity(value: String, unit: String): String = value.toFloatOrNull()?.let { financialVolume(it) + unit } ?: "--"
internal fun amount(value: String, unit: String): String = value.toFloatOrNull()?.let {
    financialVolume(if (unit == "万元") it * 10000f else it) + if (unit == "港元") "港元" else "元"
} ?: "--"
