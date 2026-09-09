package com.guet.liang.stockchat.ui

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class SelectedChartPoint(val label: String, val price: String, val value: Float, val index: Int)
