package com.guet.liang.stockchat.ui

internal class StockChatLayoutMetrics(pageWidth: Float) {
    private val referenceWidth = 400f
    private val availableWidth = pageWidth.takeIf { it > 0f } ?: referenceWidth

    val scale = (availableWidth / referenceWidth).coerceIn(0.88f, 1.12f)
    val drawerWidth = availableWidth * 0.78f
    val welcomeLogoWidth = (availableWidth * 0.34f).coerceIn(120f, 140f)
    val composerCollapsedHeight = dp(68f)
    val composerExpandedHeight = dp(112f)
    val composerFooterHeight = dp(26f)
    val composerBottomGap = dp(7f)
    val composerContentGap = dp(10f)
    val composerAttachmentStripHeight = dp(82f)

    fun dp(value: Float): Float = value * scale

    fun composerPanelHeight(
        focused: Boolean,
        voiceMode: Boolean = false,
        hasAttachments: Boolean = false,
    ): Float {
        val baseHeight = if (focused || voiceMode || hasAttachments) {
            composerExpandedHeight
        } else {
            composerCollapsedHeight
        }
        return baseHeight + if (hasAttachments) composerAttachmentStripHeight else 0f
    }

    fun composerDockHeight(
        focused: Boolean,
        voiceMode: Boolean = false,
        hasAttachments: Boolean = false,
    ): Float = composerPanelHeight(focused, voiceMode, hasAttachments) + composerFooterHeight

    fun composerContentBottom(
        bottomInset: Float,
        focused: Boolean,
        voiceMode: Boolean = false,
        hasAttachments: Boolean = false,
    ): Float = bottomInset + composerBottomGap +
        composerDockHeight(focused, voiceMode, hasAttachments) + composerContentGap
}
