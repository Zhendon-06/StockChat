package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.StockQuote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StockChatShareContentBuilderTest {
    @Test
    fun messageShareIncludesMarkdownQuoteAndRiskDisclosure() {
        val quote = testQuote()
        val message = ChatMessage(
            id = "message_1",
            role = ChatRole.ASSISTANT,
            blocks = listOf(
                AnswerBlock.Markdown(
                    source = "**短期关注成交量变化。**",
                    fallbackText = "短期关注成交量变化。",
                ),
                AnswerBlock.MarketQuote(quote),
            ),
        )

        val content = assertNotNull(StockChatShareContentBuilder.fromMessage(message))

        assertEquals("StockChat｜沪深 300（000300）", content.title)
        assertTrue(content.text.contains("短期关注成交量变化。"))
        assertTrue(content.text.contains("现价：3856.42"))
        assertTrue(content.text.contains("涨跌：+18.76 +0.49%"))
        assertTrue(content.text.endsWith(STOCK_CHAT_RISK_DISCLOSURE))
    }

    @Test
    fun quoteShareIncludesSummaryInsightAndRiskDisclosure() {
        val content = StockChatShareContentBuilder.fromQuote(testQuote())

        assertEquals("StockChat｜沪深 300（000300）行情", content.title)
        assertTrue(content.text.contains("行情摘要：权重板块多数上涨。"))
        assertTrue(content.text.contains("AI 解读：指数短线维持震荡偏强。"))
        assertTrue(content.text.endsWith(STOCK_CHAT_RISK_DISCLOSURE))
    }

    private fun testQuote(): StockQuote {
        return StockQuote(
            name = "沪深 300",
            symbol = "000300",
            marketLabel = "沪",
            price = "3856.42",
            change = "+18.76",
            changePercent = "+0.49%",
            updatedAt = "2026-09-01 15:00",
            isPositive = true,
            trendPoints = listOf(3837.66f, 3856.42f),
            summary = "权重板块多数上涨。",
            aiInsight = "指数短线维持震荡偏强。",
        )
    }
}
