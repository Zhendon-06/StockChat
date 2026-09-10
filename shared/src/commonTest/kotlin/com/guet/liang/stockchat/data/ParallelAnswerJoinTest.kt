@file:Suppress("MagicNumber", "MaxLineLength")
package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatAnswer
import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TencentMarketSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParallelAnswerJoinTest {
    private val plan = SecuritiesQueryPlan(SecuritiesIntent.QUOTE, emptyList(), needsTrend = false, needsIntraday = false)
    private val snapshot = TencentMarketSnapshot(
        providerSymbol = "sh600519",
        quote = StockQuote("贵州茅台", "600519", "沪市", "1500", "+10", "+0.67%", "09:30", true, emptyList(), "", ""),
        previousClose = "1490", open = "1495", high = "1505", low = "1490", volume = "1", volumeUnit = "手",
        amount = "1", amountUnit = "万", turnoverRate = "", priceEarningsRatio = "", amplitude = "",
    )
    private val chatSuccess = ChatAnswer.Success(listOf(AnswerBlock.Markdown("茅台偏多。", "茅台偏多。")))

    @Test
    fun streamsChatTextImmediatelyAndAttachesCardsWhenTheSlowerStockBranchFinishes() {
        val events = mutableListOf<ChatAnswer>()
        var cached: List<AnswerBlock>? = null
        val join = ParallelAnswerJoin({ events += it }) { cached = it }
        join.onChat(ChatAnswer.Streaming("茅台"))
        join.onChat(ChatAnswer.Streaming("茅台偏多。"))
        join.onChat(chatSuccess)
        assertEquals(2, events.size)
        assertNull(cached)
        join.onMarket(MarketBranchOutcome(plan, listOf(snapshot), listOf("米哈游：腾讯证券搜索暂无可用标的。")))
        val final = assertIs<ChatAnswer.Success>(events.last())
        assertEquals(3, events.size)
        val markdown = assertIs<AnswerBlock.Markdown>(final.blocks.first())
        assertEquals("茅台偏多。\n\n米哈游：腾讯证券搜索暂无可用标的。", markdown.source)
        assertEquals("贵州茅台", assertIs<AnswerBlock.MarketQuote>(final.blocks.last()).quote.name)
        assertEquals(final.blocks, cached)
    }

    @Test
    fun marketBranchFinishingFirstAttachesCardsToEveryStreamedDelta() {
        val events = mutableListOf<ChatAnswer>()
        val join = ParallelAnswerJoin({ events += it })
        join.onMarket(MarketBranchOutcome(plan, listOf(snapshot)))
        assertTrue(events.isEmpty())
        join.onChat(ChatAnswer.Streaming("茅台"))
        val streaming = assertIs<ChatAnswer.Streaming>(events.single())
        assertEquals("茅台", streaming.markdown)
        assertEquals("贵州茅台", assertIs<AnswerBlock.MarketQuote>(streaming.blocks.single()).quote.name)
        join.onChat(chatSuccess)
        val final = assertIs<ChatAnswer.Success>(events.last())
        assertEquals(2, final.blocks.size)
        assertEquals("茅台偏多。", assertIs<AnswerBlock.Markdown>(final.blocks.first()).source)
    }

    @Test
    fun cardsArrivingMidStreamAreShownImmediatelyWithTheTextSoFar() {
        val events = mutableListOf<ChatAnswer>()
        val join = ParallelAnswerJoin({ events += it })
        join.onChat(ChatAnswer.Streaming("茅台偏"))
        assertTrue(assertIs<ChatAnswer.Streaming>(events.single()).blocks.isEmpty())
        join.onMarket(MarketBranchOutcome(plan, listOf(snapshot)))
        val withCards = assertIs<ChatAnswer.Streaming>(events.last())
        assertEquals(2, events.size)
        assertEquals("茅台偏", withCards.markdown)
        assertEquals(1, withCards.blocks.size)
        join.onChat(ChatAnswer.Streaming("茅台偏多。"))
        assertEquals(1, assertIs<ChatAnswer.Streaming>(events.last()).blocks.size)
        join.onChat(chatSuccess)
        assertEquals(2, assertIs<ChatAnswer.Success>(events.last()).blocks.size)
    }

    @Test
    fun emptyMarketOutcomeMidStreamDoesNotEmitAnExtraDelta() {
        val events = mutableListOf<ChatAnswer>()
        val join = ParallelAnswerJoin({ events += it })
        join.onChat(ChatAnswer.Streaming("你好"))
        join.onMarket(MarketBranchOutcome.NONE)
        assertEquals(1, events.size)
    }

    @Test
    fun noMentionedStockLeavesTheChatAnswerUntouched() {
        val events = mutableListOf<ChatAnswer>()
        val join = ParallelAnswerJoin({ events += it })
        join.onChat(chatSuccess)
        join.onMarket(MarketBranchOutcome.NONE)
        assertEquals(chatSuccess.blocks, assertIs<ChatAnswer.Success>(events.single()).blocks)
    }

    @Test
    fun chatFailureFallsBackToVerifiableQuotesWhenTencentReturnedData() {
        val events = mutableListOf<ChatAnswer>()
        var cached = false
        val join = ParallelAnswerJoin({ events += it }) { cached = true }
        join.onChat(ChatAnswer.Failure("模型超时"))
        join.onMarket(MarketBranchOutcome(plan, listOf(snapshot)))
        val final = assertIs<ChatAnswer.Success>(events.single())
        assertTrue(assertIs<AnswerBlock.Markdown>(final.blocks.first()).source.contains("AI 深度解读当前不可用"))
        assertIs<AnswerBlock.MarketQuote>(final.blocks.last())
        assertTrue(cached)
    }

    @Test
    fun bothBranchesFailingReportsChatErrorWithStockNotices() {
        val events = mutableListOf<ChatAnswer>()
        var cached = false
        val join = ParallelAnswerJoin({ events += it }) { cached = true }
        join.onMarket(MarketBranchOutcome(notices = listOf("行情卡片暂不可用：请求超时")))
        join.onChat(ChatAnswer.Failure("模型超时"))
        assertEquals("模型超时\n行情卡片暂不可用：请求超时", assertIs<ChatAnswer.Failure>(events.single()).message)
        assertTrue(!cached)
    }

    @Test
    fun eventsAfterCompletionAndDuplicateTerminalsAreIgnored() {
        val events = mutableListOf<ChatAnswer>()
        val join = ParallelAnswerJoin({ events += it })
        join.onChat(chatSuccess)
        join.onChat(ChatAnswer.Failure("late duplicate"))
        join.onMarket(MarketBranchOutcome.NONE)
        join.onMarket(MarketBranchOutcome(plan, listOf(snapshot)))
        join.onChat(ChatAnswer.Streaming("late"))
        assertEquals(1, events.size)
        assertEquals(chatSuccess.blocks, assertIs<ChatAnswer.Success>(events.single()).blocks)
    }
}
