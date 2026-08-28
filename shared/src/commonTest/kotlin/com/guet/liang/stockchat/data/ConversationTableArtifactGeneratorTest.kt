package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.ConversationTableRowStatus
import com.guet.liang.stockchat.model.MessageState
import com.guet.liang.stockchat.model.StockQuote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ConversationTableArtifactGeneratorTest {

    @Test
    fun generatesCompletedRowFromQuestionMarkdownAndMarketQuote() {
        val snapshot = ConversationTableArtifactGenerator.generate(
            title = "贵州茅台分析",
            messages = listOf(
                userMessage(
                    id = "question",
                    text = "  请   分析\n贵州茅台 600519.SH  ",
                ),
                ChatMessage(
                    id = "answer",
                    role = ChatRole.ASSISTANT,
                    blocks = listOf(
                        AnswerBlock.Markdown(
                            source = "建议关注估值与现金流。",
                            fallbackText = "",
                        ),
                        AnswerBlock.MarketQuote(
                            quote = stockQuote(
                                name = "贵州茅台",
                                symbol = "600519.SH",
                                summary = "品牌  护城河稳固",
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals("贵州茅台分析 · 产物表格", snapshot.title)
        assertEquals(2, snapshot.sourceMessageCount)
        assertEquals(1, snapshot.rows.size)
        with(snapshot.rows.single()) {
            assertEquals(1, sequence)
            assertEquals("请 分析 贵州茅台 600519.SH", userQuestion)
            assertEquals(
                "建议关注估值与现金流。；贵州茅台（600519.SH）：品牌 护城河稳固",
                aiAnswerSummary,
            )
            assertEquals("贵州茅台（600519.SH）", relatedInstrument)
            assertEquals(ConversationTableRowStatus.COMPLETED, status)
        }
    }

    @Test
    fun marksQuestionWithoutAssistantMessageAsWaiting() {
        val snapshot = ConversationTableArtifactGenerator.generate(
            title = "待回答",
            messages = listOf(userMessage("question", "分析 600519.SH")),
        )

        with(snapshot.rows.single()) {
            assertEquals("等待 AI 回答", aiAnswerSummary)
            assertEquals("600519.SH", relatedInstrument)
            assertEquals(ConversationTableRowStatus.WAITING, status)
        }
    }

    @Test
    fun usesNormalizedFailureMessageForFailedAnswer() {
        val snapshot = ConversationTableArtifactGenerator.generate(
            title = "失败回答",
            messages = listOf(
                userMessage("question", "分析 000001.SZ"),
                ChatMessage(
                    id = "answer",
                    role = ChatRole.ASSISTANT,
                    blocks = emptyList(),
                    state = MessageState.FAILED,
                    errorMessage = "  服务   超时\n请重试  ",
                ),
            ),
        )

        with(snapshot.rows.single()) {
            assertEquals("服务 超时 请重试", aiAnswerSummary)
            assertEquals("000001.SZ", relatedInstrument)
            assertEquals(ConversationTableRowStatus.FAILED, status)
        }
    }

    @Test
    fun appendsTitleSuffixOnceAndDefaultsBlankTitle() {
        val suffixed = ConversationTableArtifactGenerator.generate(
            title = "  会话复盘   ·  产物表格  ",
            messages = emptyList(),
        )
        val blank = ConversationTableArtifactGenerator.generate(
            title = " \n\t ",
            messages = emptyList(),
        )

        assertEquals("会话复盘 · 产物表格", suffixed.title)
        assertEquals("当前会话 · 产物表格", blank.title)
    }

    @Test
    fun normalizesWhitespaceBeforeTruncatingAnswerSummary() {
        val firstPart = "甲".repeat(200)
        val secondPart = "乙".repeat(200)
        val snapshot = ConversationTableArtifactGenerator.generate(
            title = "长摘要",
            messages = listOf(
                userMessage("question", "总结回答"),
                ChatMessage(
                    id = "answer",
                    role = ChatRole.ASSISTANT,
                    blocks = listOf(
                        AnswerBlock.Markdown(
                            source = "  $firstPart  \n\t $secondPart  ",
                            fallbackText = "",
                        ),
                    ),
                ),
            ),
        )

        val expected = "$firstPart $secondPart".take(360)
        val summary = snapshot.rows.single().aiAnswerSummary
        assertEquals(expected, summary)
        assertEquals(360, summary.length)
        assertFalse(summary.contains("\n"))
        assertFalse(summary.contains("  "))
    }

    private fun userMessage(id: String, text: String): ChatMessage {
        return ChatMessage(
            id = id,
            role = ChatRole.USER,
            blocks = listOf(AnswerBlock.Markdown(source = text, fallbackText = "")),
        )
    }

    private fun stockQuote(
        name: String,
        symbol: String,
        summary: String,
    ): StockQuote {
        return StockQuote(
            name = name,
            symbol = symbol,
            marketLabel = "沪市",
            price = "1,480.00",
            change = "+10.00",
            changePercent = "+0.68%",
            updatedAt = "2026-08-28 15:00",
            isPositive = true,
            trendPoints = listOf(1f, 2f),
            summary = summary,
            aiInsight = "演示信息",
        )
    }
}
