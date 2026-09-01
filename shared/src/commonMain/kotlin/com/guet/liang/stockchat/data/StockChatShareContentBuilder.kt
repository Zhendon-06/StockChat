package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.ShareContent
import com.guet.liang.stockchat.model.StockQuote

internal const val STOCK_CHAT_RISK_DISCLOSURE =
    "StockChat Demo 信息，仅供参考，不构成投资建议。"

internal object StockChatShareContentBuilder {
    fun fromMessage(message: ChatMessage): ShareContent? {
        val body = message.blocks.mapNotNull(::blockText)
            .joinToString("\n\n")
            .trim()
        if (body.isBlank()) {
            return null
        }
        val quote = message.blocks.filterIsInstance<AnswerBlock.MarketQuote>()
            .firstOrNull()
            ?.quote
        val title = when {
            quote != null -> "StockChat｜${quote.name}（${quote.symbol}）"
            message.role == ChatRole.USER -> "StockChat｜我的提问"
            else -> "StockChat｜AI 股票问答"
        }
        return ShareContent(
            title = title,
            text = body.withRiskDisclosure(),
        )
    }

    fun fromQuote(quote: StockQuote): ShareContent {
        val body = buildString {
            append(quoteText(quote))
            if (quote.summary.isNotBlank()) {
                append("\n\n行情摘要：")
                append(quote.summary.trim())
            }
            if (quote.aiInsight.isNotBlank()) {
                append("\n\nAI 解读：")
                append(quote.aiInsight.trim())
            }
        }
        return ShareContent(
            title = "StockChat｜${quote.name}（${quote.symbol}）行情",
            text = body.withRiskDisclosure(),
        )
    }

    private fun blockText(block: AnswerBlock): String? {
        return when (block) {
            is AnswerBlock.Markdown -> block.fallbackText.ifBlank { block.source }
            is AnswerBlock.MarketQuote -> quoteText(block.quote)
            is AnswerBlock.ImageGallery -> "图片附件 × ${block.images.size}"
        }.trim().ifBlank { null }
    }

    private fun quoteText(quote: StockQuote): String {
        return buildString {
            append(quote.name)
            append("（${quote.marketLabel} · ${quote.symbol}）")
            append("\n现价：${quote.price}")
            append("\n涨跌：${quote.change} ${quote.changePercent}")
            if (quote.updatedAt.isNotBlank()) {
                append("\n更新时间：${quote.updatedAt}")
            }
        }
    }

    private fun String.withRiskDisclosure(): String {
        val content = trim()
        return if (content.contains(STOCK_CHAT_RISK_DISCLOSURE)) {
            content
        } else {
            "$content\n\n$STOCK_CHAT_RISK_DISCLOSURE"
        }
    }
}
