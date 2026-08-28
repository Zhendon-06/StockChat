package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatAnswer
import com.guet.liang.stockchat.model.ChatHistoryItem
import com.guet.liang.stockchat.model.StockDetailResult
import com.guet.liang.stockchat.model.StockQuote

internal interface StockChatDataSource {
    fun answer(
        question: String,
        history: List<ChatHistoryItem>,
        images: List<String>,
        model: String,
        attempt: Int,
        callback: (ChatAnswer) -> Unit,
    )

}

internal object MockStockChatDataSource : StockChatDataSource {
    private val quotes = listOf(
        StockQuote(
            name = "贵州茅台",
            symbol = "600519",
            marketLabel = "沪市 · Mock",
            price = "1,428.60",
            change = "+12.80",
            changePercent = "+0.90%",
            updatedAt = "本地演示数据 · 非实时 · 2026-08-27",
            isPositive = true,
            trendPoints = listOf(1411f, 1418f, 1415f, 1422f, 1419f, 1426f, 1424f, 1429f),
            summary = "演示行情显示价格小幅上行，走势数据仅用于验证行情卡片和详情页。",
            aiInsight = "该内容为本地 Mock 数据，不代表贵州茅台的真实价格、涨跌或投资价值。",
        ),
        StockQuote(
            name = "宁德时代",
            symbol = "300750",
            marketLabel = "深市 · Mock",
            price = "218.35",
            change = "-2.14",
            changePercent = "-0.97%",
            updatedAt = "本地演示数据 · 非实时 · 2026-08-27",
            isPositive = false,
            trendPoints = listOf(222f, 221f, 222f, 220f, 219f, 220f, 218f, 218.35f),
            summary = "演示行情显示价格震荡回落，走势数据仅用于验证行情卡片和详情页。",
            aiInsight = "该内容为本地 Mock 数据，不代表宁德时代的真实价格、涨跌或投资价值。",
        ),
        StockQuote(
            name = "沪深300",
            symbol = "000300",
            marketLabel = "指数 · Mock",
            price = "3,892.41",
            change = "+18.26",
            changePercent = "+0.47%",
            updatedAt = "本地演示数据 · 非实时 · 2026-08-27",
            isPositive = true,
            trendPoints = listOf(3868f, 3874f, 3871f, 3882f, 3880f, 3887f, 3885f, 3892f),
            summary = "演示指数温和上行，仅用于展示大盘问答、指数卡片和走势图。",
            aiInsight = "该内容为本地 Mock 数据，不代表沪深300的真实点位或市场判断。",
        ),
        StockQuote(
            name = "中证500",
            symbol = "000905",
            marketLabel = "指数 · Mock",
            price = "5,642.18",
            change = "-21.35",
            changePercent = "-0.38%",
            updatedAt = "本地演示数据 · 非实时 · 2026-08-27",
            isPositive = false,
            trendPoints = listOf(5670f, 5664f, 5668f, 5658f, 5661f, 5650f, 5647f, 5642f),
            summary = "演示指数震荡偏弱，仅用于展示行情卡片、涨跌状态和详情页。",
            aiInsight = "该内容为本地 Mock 数据，不代表中证500的真实点位或市场判断。",
        ),
    )

    override fun answer(
        question: String,
        history: List<ChatHistoryItem>,
        images: List<String>,
        model: String,
        attempt: Int,
        callback: (ChatAnswer) -> Unit,
    ) {
        val quote = quoteForText(question)
        val markdown = if (quote == null) {
            "当前未配置 AI API Key，正在使用本地 Mock 模式。你可以试试“分析一下贵州茅台”或“看看沪深300指数”。\n\n仅供演示，不构成投资建议。"
        } else {
            "下面展示的是 **${quote.name}（${quote.symbol}）的本地 Mock 行情**，用于验证行情卡片和详情页，不是实时数据。\n\n仅供演示，不构成投资建议。"
        }
        callback(
            ChatAnswer.Success(
                buildList {
                    add(AnswerBlock.Markdown(markdown, markdown))
                    quote?.let { add(AnswerBlock.MarketQuote(it)) }
                }
            )
        )
    }

    fun stockDetail(symbol: String): StockDetailResult {
        val normalizedSymbol = symbol.trim().uppercase()
        val quote = quotes.firstOrNull {
            it.symbol == normalizedSymbol || "SH${it.symbol}" == normalizedSymbol ||
                "SZ${it.symbol}" == normalizedSymbol
        }
        return quote?.let(StockDetailResult::Success) ?: StockDetailResult.Empty
    }

    fun quoteForText(text: String): StockQuote? {
        val normalizedText = text.trim().uppercase().replace(" ", "")
        return when {
            "贵州茅台" in normalizedText || "茅台" in normalizedText || "600519" in normalizedText ->
                quotes.first { it.symbol == "600519" }
            "宁德时代" in normalizedText || "宁德" in normalizedText || "300750" in normalizedText ->
                quotes.first { it.symbol == "300750" }
            "中证500" in normalizedText || "000905" in normalizedText ->
                quotes.first { it.symbol == "000905" }
            "沪深300" in normalizedText || "HS300" in normalizedText || "000300" in normalizedText ||
                "大盘" in normalizedText -> quotes.first { it.symbol == "000300" }
            else -> null
        }
    }
}
