package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ChatHistoryItem
import com.guet.liang.stockchat.model.ChatRole

/** Keeps the system prompt and newest turn inside the selected model's input budget. */
internal object ContextWindowManager {
    fun trim(
        history: List<ChatHistoryItem>,
        question: String,
        contextWindowTokens: Int,
        reservedOutputTokens: Int = 1024,
        systemPromptTokens: Int = 700,
    ): List<ChatHistoryItem> {
        val budget = (contextWindowTokens - reservedOutputTokens - systemPromptTokens).coerceAtLeast(MIN_HISTORY_TOKENS)
        // The current question is appended after history and must be included in the budget.
        var remaining = (budget - estimateTokens(question)).coerceAtLeast(MIN_HISTORY_TOKENS)
        val result = ArrayDeque<ChatHistoryItem>()
        history.asReversed().forEach { item ->
            if (remaining <= 0) return@forEach
            val allowedChars = (remaining * CHARS_PER_TOKEN).coerceAtLeast(MIN_ITEM_CHARS)
            val content = item.content.trim().take(allowedChars)
            if (content.isNotBlank()) {
                val normalized = ChatHistoryItem(item.role, content)
                result.addFirst(normalized)
                remaining -= estimateTokens(content)
            }
        }
        while (result.firstOrNull()?.role == ChatRole.ASSISTANT) result.removeFirst()
        return result.toList()
    }

    fun parseContextWindow(label: String, fallback: Int = DEFAULT_CONTEXT_TOKENS): Int {
        val value = Regex("(\\d+(?:\\.\\d+)?)\\s*([km]?)", RegexOption.IGNORE_CASE).find(label.trim()) ?: return fallback
        val amount = value.groupValues[1].toDoubleOrNull() ?: return fallback
        val multiplier = if (value.groupValues[2].equals("m", true)) 1_000_000 else 1_000
        return (amount * multiplier).toInt().coerceAtLeast(MIN_HISTORY_TOKENS)
    }

    private fun estimateTokens(text: String): Int = ((text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN).coerceAtLeast(1)

    private const val CHARS_PER_TOKEN = 4
    private const val MIN_ITEM_CHARS = 64
    private const val MIN_HISTORY_TOKENS = 512
    const val DEFAULT_CONTEXT_TOKENS = 8_192
}
