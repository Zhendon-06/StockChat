package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.ConversationTableArtifactSnapshot
import com.guet.liang.stockchat.model.ConversationTableRow
import com.guet.liang.stockchat.model.ConversationTableRowStatus
import com.guet.liang.stockchat.model.MessageState

internal object ConversationTableArtifactGenerator {
    fun generate(
        title: String,
        messages: List<ChatMessage>,
    ): ConversationTableArtifactSnapshot {
        val rows = mutableListOf<ConversationTableRow>()
        var pendingQuestion: ChatMessage? = null

        messages.forEach { message ->
            when (message.role) {
                ChatRole.USER -> {
                    pendingQuestion?.let { question ->
                        rows += buildRow(rows.size + 1, question, null)
                    }
                    pendingQuestion = message
                }
                ChatRole.ASSISTANT -> {
                    pendingQuestion?.let { question ->
                        rows += buildRow(rows.size + 1, question, message)
                        pendingQuestion = null
                    }
                }
            }
        }
        pendingQuestion?.let { question ->
            rows += buildRow(rows.size + 1, question, null)
        }

        val normalizedTitle = title
            .normalizeCellText(MAX_TITLE_LENGTH)
            .ifBlank { DEFAULT_ARTIFACT_TITLE }
        return ConversationTableArtifactSnapshot(
            title = if (normalizedTitle.endsWith(ARTIFACT_TITLE_SUFFIX)) {
                normalizedTitle
            } else {
                "$normalizedTitle$ARTIFACT_TITLE_SUFFIX"
            },
            sourceMessageCount = messages.size,
            rows = rows,
        )
    }

    private fun buildRow(
        sequence: Int,
        question: ChatMessage,
        answer: ChatMessage?,
    ): ConversationTableRow {
        return ConversationTableRow(
            sequence = sequence,
            userQuestion = questionText(question),
            aiAnswerSummary = answerSummary(answer),
            relatedInstrument = relatedInstrument(question, answer),
            status = rowStatus(answer),
        )
    }

    private fun questionText(message: ChatMessage): String {
        val markdown = markdownText(message)
        if (markdown.isNotBlank()) {
            return markdown.normalizeCellText(MAX_QUESTION_LENGTH)
        }
        val imageCount = message.blocks
            .filterIsInstance<AnswerBlock.ImageGallery>()
            .sumOf { it.images.size }
        return if (imageCount > 0) {
            "图片提问（$imageCount 张）"
        } else {
            EMPTY_QUESTION_TEXT
        }
    }

    private fun answerSummary(message: ChatMessage?): String {
        if (message == null) {
            return EMPTY_ANSWER_TEXT
        }
        if (message.state == MessageState.FAILED) {
            return message.errorMessage
                .normalizeCellText(MAX_ANSWER_LENGTH)
                .ifBlank { FAILED_ANSWER_TEXT }
        }

        val markdown = markdownText(message)
        val quoteSummary = message.blocks
            .filterIsInstance<AnswerBlock.MarketQuote>()
            .joinToString("；") { block ->
                val quote = block.quote
                "${quote.name}（${quote.symbol}）：${quote.summary}"
            }
        return listOf(markdown, quoteSummary)
            .filter(String::isNotBlank)
            .joinToString("；")
            .normalizeCellText(MAX_ANSWER_LENGTH)
            .ifBlank {
                if (message.state == MessageState.GENERATING) {
                    GENERATING_ANSWER_TEXT
                } else {
                    EMPTY_ANSWER_SUMMARY_TEXT
                }
            }
    }

    private fun relatedInstrument(
        question: ChatMessage,
        answer: ChatMessage?,
    ): String {
        val messages = listOfNotNull(question, answer)
        val quoteLabels = messages
            .flatMap { message -> message.blocks.filterIsInstance<AnswerBlock.MarketQuote>() }
            .map { block -> "${block.quote.name}（${block.quote.symbol}）" }
            .distinct()
        if (quoteLabels.isNotEmpty()) {
            return quoteLabels.joinToString("、").normalizeCellText(MAX_INSTRUMENT_LENGTH)
        }

        val symbols = messages
            .flatMap { message -> STOCK_SYMBOL_REGEX.findAll(markdownText(message)).map { it.value }.toList() }
            .map(String::uppercase)
            .distinct()
            .take(MAX_RELATED_SYMBOLS)
        return symbols
            .joinToString("、")
            .ifBlank { UNKNOWN_INSTRUMENT_TEXT }
            .normalizeCellText(MAX_INSTRUMENT_LENGTH)
    }

    private fun rowStatus(message: ChatMessage?): ConversationTableRowStatus {
        return when (message?.state) {
            MessageState.DELIVERED -> ConversationTableRowStatus.COMPLETED
            MessageState.GENERATING -> ConversationTableRowStatus.GENERATING
            MessageState.FAILED -> ConversationTableRowStatus.FAILED
            null -> ConversationTableRowStatus.WAITING
        }
    }

    private fun markdownText(message: ChatMessage): String {
        return message.blocks
            .filterIsInstance<AnswerBlock.Markdown>()
            .joinToString("\n") { block -> block.source.ifBlank { block.fallbackText } }
    }

    private fun String.normalizeCellText(maxLength: Int): String {
        return trim()
            .replace(WHITESPACE_REGEX, " ")
            .take(maxLength)
            .trim()
    }

    private const val MAX_TITLE_LENGTH = 60
    private const val MAX_QUESTION_LENGTH = 240
    private const val MAX_ANSWER_LENGTH = 360
    private const val MAX_INSTRUMENT_LENGTH = 120
    private const val MAX_RELATED_SYMBOLS = 4
    private const val DEFAULT_ARTIFACT_TITLE = "当前会话"
    private const val ARTIFACT_TITLE_SUFFIX = " · 产物表格"
    private const val EMPTY_QUESTION_TEXT = "未提供文字问题"
    private const val EMPTY_ANSWER_TEXT = "等待 AI 回答"
    private const val FAILED_ANSWER_TEXT = "AI 回答生成失败"
    private const val GENERATING_ANSWER_TEXT = "AI 正在生成回答"
    private const val EMPTY_ANSWER_SUMMARY_TEXT = "暂无可摘要的文字回答"
    private const val UNKNOWN_INSTRUMENT_TEXT = "未识别"
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val STOCK_SYMBOL_REGEX = Regex(
        pattern = "(?:SH|SZ)?\\d{6}(?:\\.(?:SH|SZ))?",
        option = RegexOption.IGNORE_CASE,
    )
}
