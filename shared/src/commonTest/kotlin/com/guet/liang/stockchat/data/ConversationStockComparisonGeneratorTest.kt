package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.ConversationStockDataSource
import com.guet.liang.stockchat.model.ConversationTableRowStatus
import com.guet.liang.stockchat.model.StockQuote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationStockComparisonGeneratorTest {
    @Test
    fun extractsEveryUserAndAiSecurityWithoutSessionCapAndDeduplicates() {
        val snapshot = ConversationStockComparisonGenerator.generate(
            title = "持仓讨论",
            messages = listOf(
                markdownMessage(
                    id = "user",
                    role = ChatRole.USER,
                    text = "比较贵州茅台、宁德时代、002594 和腾讯控股",
                ),
                ChatMessage(
                    id = "assistant",
                    role = ChatRole.ASSISTANT,
                    blocks = listOf(
                        AnswerBlock.Markdown(
                            source = "还可以参考 601318.SH 与美团-W。",
                            fallbackText = "",
                        ),
                        AnswerBlock.MarketQuote(
                            stockQuote(
                                name = "腾讯控股",
                                symbol = "00700",
                                marketLabel = "港股 · 腾讯行情",
                            )
                        ),
                    ),
                ),
            ),
        )

        assertEquals("持仓讨论 · 表格对比", snapshot.title)
        assertEquals(6, snapshot.rows.size)
        assertEquals(
            setOf("sh600519", "sz300750", "sz002594", "hk00700", "sh601318", "hk03690"),
            snapshot.providerSymbols.toSet(),
        )
        with(snapshot.rows.single { it.providerSymbol == "hk00700" }) {
            assertTrue(mentionedByUser)
            assertTrue(generatedByAi)
            assertEquals(listOf("user", "assistant"), relatedMessageIds)
            assertEquals(ConversationStockDataSource.CONVERSATION_QUOTE, dataSource)
        }
        with(snapshot.rows.single { it.providerSymbol == "sh601318" }) {
            assertFalse(mentionedByUser)
            assertTrue(generatedByAi)
        }
    }

    @Test
    fun quoteFallbackExtractsTradingMetricsAndIgnoresMetricNumbers() {
        val summary = "昨收 9.80，今开 10.00，最高 10.50，最低 9.70，" +
            "成交量 123456 手，成交额 208601 万元，换手率 1.50%，市盈率 20.20，振幅 8.00%"
        val snapshot = ConversationStockComparisonGenerator.generate(
            title = "指标",
            messages = listOf(
                markdownMessage(
                    id = "user",
                    role = ChatRole.USER,
                    text = "成交量 123456 手，成交额 208601 万元，请看 600519",
                ),
                ChatMessage(
                    id = "assistant",
                    role = ChatRole.ASSISTANT,
                    blocks = listOf(
                        AnswerBlock.MarketQuote(
                            stockQuote(
                                name = "贵州茅台",
                                symbol = "600519",
                                marketLabel = "沪市 · 腾讯行情",
                                summary = summary,
                            )
                        ),
                    ),
                ),
            ),
        )

        assertEquals(1, snapshot.rows.size)
        with(snapshot.rows.single()) {
            assertEquals("sh600519", providerSymbol)
            assertEquals("9.80", previousClose)
            assertEquals("10.00", open)
            assertEquals("10.50", high)
            assertEquals("9.70", low)
            assertEquals("123456", volume)
            assertEquals("手", volumeUnit)
            assertEquals("208601", amount)
            assertEquals("万元", amountUnit)
            assertEquals("1.50", turnoverRate)
            assertEquals("20.20", priceEarningsRatio)
            assertEquals("8.00", amplitude)
        }
    }

    @Test
    fun extractsArbitraryBareCodesFromPlainNaturalAndLongListText() {
        val snapshot = ConversationStockComparisonGenerator.generate(
            title = "任意代码",
            messages = listOf(
                markdownMessage("plain", ChatRole.USER, "603259"),
                markdownMessage("natural", ChatRole.USER, "688012怎么样"),
                markdownMessage(
                    id = "long-list",
                    role = ChatRole.USER,
                    text = "对比以下股票：002371、300015、600276、601899、000725、832982",
                ),
            ),
        )

        assertEquals(
            listOf(
                "sh603259",
                "sh688012",
                "sz002371",
                "sz300015",
                "sh600276",
                "sh601899",
                "sz000725",
                "bj832982",
            ),
            snapshot.providerSymbols,
        )
    }

    @Test
    fun rejectsNaturalLanguageMetricValuesEvenNearStockKeywords() {
        val snapshot = ConversationStockComparisonGenerator.generate(
            title = "指标数字",
            messages = listOf(
                markdownMessage(
                    id = "question",
                    role = ChatRole.USER,
                    text = "这只股票成交量为123456手，成交额：208601万元，总市值约为345678元，" +
                        "另一项成交量：600519手；股票代码为603259。",
                )
            ),
        )

        assertEquals(listOf("sh603259"), snapshot.providerSymbols)
    }

    @Test
    fun knownIndexCodesUseCatalogMarketAndDedupeAliases() {
        val snapshot = ConversationStockComparisonGenerator.generate(
            title = "指数代码",
            messages = listOf(
                markdownMessage(
                    id = "question",
                    role = ChatRole.USER,
                    text = "000300、沪深300（000300）、中证500 000905、上证指数000001",
                )
            ),
        )

        assertEquals(
            listOf("sh000300", "sh000905", "sh000001"),
            snapshot.providerSymbols,
        )
        assertEquals(3, snapshot.rows.size)
    }

    @Test
    fun explicitMarketCodesRequireNumericBoundaries() {
        val snapshot = ConversationStockComparisonGenerator.generate(
            title = "代码边界",
            messages = listOf(
                markdownMessage(
                    id = "question",
                    role = ChatRole.USER,
                    text = "忽略 sh6005190、1600519.SH、sz3007501；保留 sh600519、300750.SZ",
                )
            ),
        )

        assertEquals(listOf("sh600519", "sz300750"), snapshot.providerSymbols)
    }

    @Test
    fun sentencePeriodDoesNotRejectBareCodeButDecimalValueDoes() {
        val snapshot = ConversationStockComparisonGenerator.generate(
            title = "句末标点",
            messages = listOf(
                markdownMessage(
                    id = "question",
                    role = ChatRole.USER,
                    text = "603259. 688012。现价 654321.50 元。",
                )
            ),
        )

        assertEquals(listOf("sh603259", "sh688012"), snapshot.providerSymbols)
    }

    @Test
    fun freshSnapshotOverlaysMetricsWhilePreservingConversationSources() {
        val conversationSnapshot = ConversationStockComparisonGenerator.generate(
            title = "刷新",
            messages = listOf(
                markdownMessage("user", ChatRole.USER, "分析贵州茅台"),
                ChatMessage(
                    id = "assistant",
                    role = ChatRole.ASSISTANT,
                    blocks = listOf(AnswerBlock.MarketQuote(stockQuote())),
                ),
            ),
        )

        val updated = ConversationStockComparisonGenerator.overlay(
            snapshot = conversationSnapshot,
            marketSnapshots = listOf(marketSnapshot("sh600519", price = "1,520.00")),
        ).rows.single()

        assertEquals("1,520.00", updated.price)
        assertEquals("1,490.00", updated.previousClose)
        assertEquals("1,498.00", updated.open)
        assertEquals("1,525.00", updated.high)
        assertEquals("1,488.00", updated.low)
        assertEquals("18.20", updated.priceEarningsRatio)
        assertEquals(ConversationStockDataSource.FRESH_MARKET, updated.dataSource)
        assertTrue(updated.mentionedByUser)
        assertTrue(updated.generatedByAi)
        assertEquals(listOf("user", "assistant"), updated.relatedMessageIds)
    }

    @Test
    fun adaptsComparisonRowsToLegacyArtifactWithoutQuestionAnswerDump() {
        val comparison = ConversationStockComparisonGenerator.generate(
            title = "会话",
            messages = listOf(
                markdownMessage("user", ChatRole.USER, "比较贵州茅台和宁德时代"),
                ChatMessage(
                    id = "assistant",
                    role = ChatRole.ASSISTANT,
                    blocks = listOf(AnswerBlock.MarketQuote(stockQuote())),
                ),
            ),
        )

        val artifact = ConversationStockComparisonGenerator.toArtifactSnapshot(comparison)

        assertEquals(2, artifact.rows.size)
        with(artifact.rows.single { it.relatedInstrument.contains("贵州茅台") }) {
            assertEquals("用户提及 + AI 生成", userQuestion)
            assertTrue(aiAnswerSummary.contains("现价"))
            assertEquals(ConversationTableRowStatus.COMPLETED, status)
        }
        with(artifact.rows.single { it.relatedInstrument.contains("宁德时代") }) {
            assertEquals("待获取最新行情", aiAnswerSummary)
            assertEquals(ConversationTableRowStatus.WAITING, status)
        }
    }

    private fun markdownMessage(
        id: String,
        role: ChatRole,
        text: String,
    ): ChatMessage {
        return ChatMessage(
            id = id,
            role = role,
            blocks = listOf(AnswerBlock.Markdown(source = text, fallbackText = "")),
        )
    }

    private fun stockQuote(
        name: String = "贵州茅台",
        symbol: String = "600519",
        marketLabel: String = "沪市 · 腾讯行情",
        summary: String = "昨收 1,490.00，今开 1,498.00",
    ): StockQuote {
        return StockQuote(
            name = name,
            symbol = symbol,
            marketLabel = marketLabel,
            price = "1,500.00",
            change = "+10.00",
            changePercent = "+0.67%",
            updatedAt = "腾讯行情 · 2026-08-31 15:00:00",
            isPositive = true,
            trendPoints = listOf(1490f, 1500f),
            summary = summary,
            aiInsight = "演示解读",
        )
    }

    private fun marketSnapshot(
        providerSymbol: String,
        price: String,
    ): TencentMarketSnapshot {
        return TencentMarketSnapshot(
            providerSymbol = providerSymbol,
            quote = stockQuote().copy(price = price),
            previousClose = "1,490.00",
            open = "1,498.00",
            high = "1,525.00",
            low = "1,488.00",
            volume = "12000",
            volumeUnit = "手",
            amount = "180000",
            amountUnit = "万元",
            turnoverRate = "0.55",
            priceEarningsRatio = "18.20",
            amplitude = "2.48",
        )
    }
}
