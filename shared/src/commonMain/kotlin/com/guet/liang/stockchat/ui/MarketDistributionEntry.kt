package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklychart.ui.PieChart
import com.guet.liang.kuiklychart.api.ChartLegendPosition
import com.guet.liang.kuiklychart.api.PieEntry
import com.guet.liang.kuiklychart.api.PieLabelMode
import com.guet.liang.kuiklychart.finance.financialNumber
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class MarketDistributionEntry(val label: String, val magnitude: Float, val valueLabel: String, val color: Color)

/** Labels remain outside the ring, legible on narrow phones; selection is shown in its center. */
