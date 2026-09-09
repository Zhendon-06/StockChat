package com.guet.liang.stockchat.ui

// 详情页 UI 状态模型：页签、解读焦点、行情与预测加载态。

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal enum class DetailInsightFocus(val label: String) {
    TREND("走势解读"),
    PREDICTION("预测依据"),
    RISK("风险提醒"),
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal enum class DetailTab(val label: String) {
    MARKET("行情展示"),
    PREDICTION("AI 走势"),
}

internal fun scaledFontSize(baseSize: Float): Float = baseSize * StockChatTheme.fontScale
