package com.guet.liang.stockchat.ui

import com.tencent.kuikly.core.views.Text

/**
 * 估算文本在给定可用宽度内折行后的行数。
 * Kuikly 的 Text/TextArea 只有在自身宽度确定时才会折行，
 * 对于内容撑宽（shrink-to-fit）的场景需要先估算是否超宽，再决定是否给定宽度。
 */
internal fun estimateWrappedLineCount(text: String, fontSize: Float, availableWidth: Float): Int {
    if (text.isEmpty() || availableWidth <= 0f) {
        return 1
    }
    var lines = 1
    var lineWidth = 0f
    for (ch in text) {
        if (ch == '\n') {
            lines += 1
            lineWidth = 0f
            continue
        }
        // CJK/全角字符约等于字号宽，拉丁字符按经验比例估算
        val charWidth = if (ch.code > 0x2E7F) fontSize else fontSize * 0.56f
        if (lineWidth > 0f && lineWidth + charWidth > availableWidth) {
            lines += 1
            lineWidth = charWidth
        } else {
            lineWidth += charWidth
        }
    }
    return lines
}
