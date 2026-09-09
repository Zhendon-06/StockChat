package com.guet.liang.stockchat.ui

import com.tencent.kuikly.core.base.Attr
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle

/** Shared semantic styles keep card surfaces consistent across pages and themes. */
internal fun Attr.themedBorder() {
    border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
}

/** Applies the standard surface and border used by cards in the chat experience. */
internal fun Attr.cardSurface() {
    backgroundColor(StockChatTheme.surface)
    themedBorder()
}
