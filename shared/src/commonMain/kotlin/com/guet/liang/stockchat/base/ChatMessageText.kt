package com.guet.liang.stockchat.base

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatMessage

internal fun messageText(message: ChatMessage): String {
    return message.blocks
        .mapNotNull { block ->
            when (block) {
                    is AnswerBlock.Markdown -> block.fallbackText.ifBlank { block.source }
                    is AnswerBlock.MarketQuote ->
                        "${block.quote.name}（${block.quote.symbol}） ${block.quote.price} " +
                            "${block.quote.change} ${block.quote.changePercent}"
                    is AnswerBlock.ImageGallery -> "图片附件 × ${block.images.size}"
                }
                .trim()
                .ifBlank { null }
        }
        .joinToString("\n\n")
        .trim()
}
