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
        attempt: Int,
        callback: (ChatAnswer) -> Unit,
    )

    fun stockDetail(symbol: String): StockDetailResult
}

internal object MockStockChatDataSource : StockChatDataSource {
    private val quotes = listOf(
        StockQuote(
            name = "贵州茅台",
            symbol = "600519",
            marketLabel = "上证",
            price = "1,438.20",
            change = "+18.35",
            changePercent = "+1.29%",
            updatedAt = "演示行情 · 15:00 收盘",
            isPositive = true,
            trendPoints = listOf(35f, 31f, 33f, 26f, 29f, 21f, 23f, 16f, 19f, 12f, 14f, 9f),
            summary = "价格位于近期区间中上部，日内量价表现偏强。消费板块整体波动仍高于近五日均值。",
            aiInsight = "短线动能有所恢复，但上方仍有前期成交密集区。更适合结合仓位与风险承受能力观察，不宜仅凭单日涨幅作判断。",
        ),
        StockQuote(
            name = "沪深300",
            symbol = "000300",
            marketLabel = "指数",
            price = "3,982.64",
            change = "-12.18",
            changePercent = "-0.30%",
            updatedAt = "演示行情 · 15:00 收盘",
            isPositive = false,
            trendPoints = listOf(10f, 14f, 12f, 19f, 17f, 23f, 20f, 26f, 24f, 31f, 29f, 34f),
            summary = "指数午后震荡走弱，权重板块分化，市场成交活跃度较前一交易日小幅回落。",
            aiInsight = "当前更接近结构性行情，指数方向信号有限。可继续观察成交额与核心权重能否同步企稳。",
        ),
    )

    override fun answer(
        question: String,
        history: List<ChatHistoryItem>,
        attempt: Int,
        callback: (ChatAnswer) -> Unit,
    ) {
        if (attempt == 0 && (question.contains("失败") || question.contains("error", ignoreCase = true))) {
            callback(ChatAnswer.Failure("演示服务暂时没有返回结果，请稍后重试。"))
            return
        }

        val quote = quoteForQuestion(question) ?: quotes[0]
        val isRiskQuestion = question.contains("风险") || question.contains("注意")
        val markdown = if (isRiskQuestion) {
            "### 风险观察\n\n- 关注成交额与波动率变化\n- 不要把单日涨跌当作趋势确认\n- 结合仓位与自身风险承受能力判断"
        } else if (quote.symbol == "000300") {
            "### 今日指数观察\n\n- 沪深300收跌 **0.30%**\n- 权重板块表现分化\n- 成交活跃度小幅回落"
        } else {
            "### 行情速览\n\n贵州茅台今日收涨 **1.29%**，价格表现偏强。短线可关注成交量延续性与前高附近压力。"
        }
        val fallback = if (isRiskQuestion) {
            "风险观察\n关注成交额与波动率变化，不要把单日涨跌当作趋势确认，并结合自身风险承受能力判断。"
        } else if (quote.symbol == "000300") {
            "今日指数观察\n沪深300收跌 0.30%，权重板块表现分化，成交活跃度小幅回落。"
        } else {
            "行情速览\n贵州茅台今日收涨 1.29%，价格表现偏强。短线可关注成交量延续性与前高附近压力。"
        }
        val blocks = mutableListOf<AnswerBlock>(AnswerBlock.Markdown(markdown, fallback))
        if (!isRiskQuestion) {
            blocks += AnswerBlock.MarketQuote(quote)
        }
        callback(ChatAnswer.Success(blocks))
    }

    fun quoteForQuestion(question: String): StockQuote? {
        if (question.contains("风险") || question.contains("注意")) {
            return null
        }
        return if (
            question.contains("大盘") ||
            question.contains("指数") ||
            question.contains("沪深") ||
            question.contains("000300")
        ) {
            quotes[1]
        } else if (
            question.contains("茅台") ||
            question.contains("600519")
        ) {
            quotes[0]
        } else {
            null
        }
    }

    override fun stockDetail(symbol: String): StockDetailResult {
        val normalizedSymbol = symbol.trim().uppercase()
        if (normalizedSymbol == "ERROR") {
            return StockDetailResult.Failure("演示行情加载失败，请检查网络后重试。")
        }
        val quote = quotes.firstOrNull { it.symbol == normalizedSymbol }
            ?: return StockDetailResult.Empty
        return StockDetailResult.Success(quote)
    }
}
