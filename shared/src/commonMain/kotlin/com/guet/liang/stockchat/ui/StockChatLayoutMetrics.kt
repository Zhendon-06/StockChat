package com.guet.liang.stockchat.ui

import com.tencent.kuikly.core.pager.Pager

internal fun Pager.contentWidth(): Float = (pagerData.pageViewWidth - 32f).coerceAtLeast(1f)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal class StockChatLayoutMetrics(pageWidth: Float) {
    private val referenceWidth = 400f
    private val availableWidth = pageWidth.takeIf { it > 0f } ?: referenceWidth
    private val viewportScale = (availableWidth / referenceWidth).coerceIn(0.88f, 1.12f)
    private val fontScale = StockChatTheme.fontScale

    val scale = viewportScale * fontScale
    val drawerWidth = availableWidth * 0.78f
    // 欢迎页主视觉（方形图形 logo）边长：接近 WorkBuddy 吉祥物的视觉分量
    val welcomeHeroSize = (availableWidth * 0.36f).coerceIn(130f, 162f)
    val composerCollapsedHeight = dp(68f)
    val composerExpandedHeight = dp(112f)
    // footer + gap 共同决定面板离屏幕底的距离；面板底 = max(键盘, 安全区) + gap + footer，
    // 安全区兜底保证不压小白条，这里只留少量呼吸空隙
    val composerFooterHeight = dp(10f)
    val composerBottomGap = dp(4f)
    // 输入框上缘的内容渐隐过渡高度：消息延伸到面板顶边，最后这段淡出到页面背景
    val composerContentFadeHeight = dp(36f)
    val composerAttachmentStripHeight = dp(82f)
    // 输入框单行高度，多行时面板按行同步增高
    val composerInputLineHeight = dp(23f)

    fun dp(value: Float): Float = value * scale

    fun composerPanelHeight(
        focused: Boolean,
        voiceMode: Boolean = false,
        hasAttachments: Boolean = false,
        extraInputLines: Int = 0,
    ): Float {
        val baseHeight =
            if (focused || voiceMode || hasAttachments) {
                composerExpandedHeight
            } else {
                composerCollapsedHeight
            }
        return baseHeight +
            composerInputLineHeight * extraInputLines.coerceAtLeast(0) +
            if (hasAttachments) composerAttachmentStripHeight else 0f
    }

    fun composerDockHeight(focused: Boolean, voiceMode: Boolean = false, hasAttachments: Boolean = false, extraInputLines: Int = 0): Float =
        composerPanelHeight(focused, voiceMode, hasAttachments, extraInputLines) + composerFooterHeight

    // Android 某些系统/输入法会把 IME 高度暂时合并进 safeAreaInsets.bottom，
    // 键盘收起后这个值还可能滞后一帧。先把安全区限制在导航栏的合理范围，
    // 键盘态再扣除其中可能重复的部分，避免输入面板被重复上推。
    private val composerKeyboardClearance = dp(20f)
    private val maxNavigationBarInset = dp(48f)

    fun composerBottomInset(keyboardHeight: Float, safeAreaBottom: Float): Float {
        val navigationBarInset = safeAreaBottom.coerceIn(0f, maxNavigationBarInset)
        if (keyboardHeight <= 0.5f) {
            return navigationBarInset
        }
        val duplicatedInset = (safeAreaBottom - navigationBarInset).coerceAtLeast(0f)
        return (keyboardHeight - duplicatedInset).coerceAtLeast(0f) + composerKeyboardClearance
    }

    fun composerContentBottom(
        bottomInset: Float,
        focused: Boolean,
        voiceMode: Boolean = false,
        hasAttachments: Boolean = false,
        extraInputLines: Int = 0,
    ): Float = bottomInset + composerBottomGap + composerDockHeight(focused, voiceMode, hasAttachments, extraInputLines)
}
