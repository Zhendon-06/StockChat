package com.guet.liang.stockchat.ui

internal class StockChatLayoutMetrics(pageWidth: Float) {
    private val referenceWidth = 400f
    private val availableWidth = pageWidth.takeIf { it > 0f } ?: referenceWidth

    val scale = (availableWidth / referenceWidth).coerceIn(0.88f, 1.12f)
    val drawerWidth = availableWidth * 0.78f
    val welcomeLogoWidth = (availableWidth * 0.30f).coerceIn(110f, 128f)
    val composerCollapsedHeight = dp(68f)
    val composerExpandedHeight = dp(112f)
    val composerFooterHeight = dp(26f)
    val composerBottomGap = dp(7f)
    val composerContentGap = dp(10f)

    fun dp(value: Float): Float = value * scale

    fun composerPanelHeight(focused: Boolean): Float =
        if (focused) composerExpandedHeight else composerCollapsedHeight

    fun composerDockHeight(focused: Boolean): Float =
        composerPanelHeight(focused) + composerFooterHeight

    fun composerContentBottom(bottomInset: Float, focused: Boolean): Float =
        bottomInset + composerBottomGap + composerDockHeight(focused) + composerContentGap
}
