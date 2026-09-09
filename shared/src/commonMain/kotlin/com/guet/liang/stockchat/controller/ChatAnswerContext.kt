package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.data.providerSymbolForQuote
import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatHistoryItem
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole

private const val MAX_HISTORY_TURNS = 6

internal fun imagesBeforeAnswer(messages: List<ChatMessage>, messageId: String): List<String> {
    val answerIndex = messages.indexOfFirst { it.id == messageId }
    if (answerIndex <= 0) {
        return emptyList()
    }
    for (index in (answerIndex - 1) downTo 0) {
        val message = messages[index]
        if (message.role == ChatRole.USER) {
            return message.blocks
                .filterIsInstance<AnswerBlock.ImageGallery>()
                .flatMap { it.requestImages }
                .filter { it.startsWith("data:image/") }
        }
    }
    return emptyList()
}

internal fun conversationHistoryBefore(messages: List<ChatMessage>, messageId: String): List<ChatHistoryItem> {
    val answerIndex = messages.indexOfFirst { it.id == messageId }
    if (answerIndex < 0) {
        return emptyList()
    }
    val historyItems =
        messages.take(answerIndex).mapNotNull { message ->
            val content =
                message.blocks
                    .mapNotNull { block ->
                        when (block) {
                            is AnswerBlock.Markdown -> block.source.trim().ifEmpty { null }
                            is AnswerBlock.MarketQuote ->
                                providerSymbolForQuote(block.quote)?.let { providerSymbol ->
                                    "[行情标的:$providerSymbol|${block.quote.name}] " +
                                        "${block.quote.updatedAt}，现价 ${block.quote.price}，" +
                                        "涨跌 ${block.quote.change}（${block.quote.changePercent}）"
                                }
                            is AnswerBlock.ImageGallery -> null
                        }
                    }
                    .joinToString("\n\n")
                    .trim()
            if (content.isEmpty()) {
                null
            } else {
                ChatHistoryItem(message.role, content)
            }
        }
    val completedTurns = mutableListOf<ChatHistoryItem>()
    var pendingUserMessage: ChatHistoryItem? = null
    historyItems.forEach { item ->
        when (item.role) {
            ChatRole.USER -> pendingUserMessage = item
            ChatRole.ASSISTANT ->
                pendingUserMessage?.let { userMessage ->
                    completedTurns += userMessage
                    completedTurns += item
                    pendingUserMessage = null
                }
        }
    }
    return completedTurns.takeLast(MAX_HISTORY_TURNS * 2)
}
