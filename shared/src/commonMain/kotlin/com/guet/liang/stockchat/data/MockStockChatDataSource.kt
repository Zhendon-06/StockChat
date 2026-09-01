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
        val markdown = if (quote != null) {
            "下面展示的是 **${quote.name}（${quote.symbol}）的本地 Mock 行情**，用于验证行情卡片和详情页，不是实时数据。\n\n仅供演示，不构成投资建议。"
        } else {
            localEducationAnswer(question) ?: "当前未配置 AI API Key，正在使用本地 Mock 模式。" +
                "配置后可回答行情、投资知识和其他通用问题；现在可以试试“新手怎么开始炒股”" +
                "或“什么是市盈率”。\n\n仅供演示，不构成投资建议。"
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

    private fun localEducationAnswer(question: String): String? {
        val normalized = question.replace(Regex("\\s+"), "")
        return when {
            listOf("怎么炒股", "如何炒股", "怎么买股票", "如何买股票", "新手").any {
                normalized.contains(it)
            } -> """
                **新手可以按这个顺序开始：**

                1. 先了解交易规则、费用、涨跌风险和退市风险。
                2. 只使用不影响生活的闲钱，先设总仓位和单笔亏损上限。
                3. 通过合规券商开户，先用模拟盘熟悉下单、撤单和复盘。
                4. 从自己能理解的行业与公司开始，记录买入理由、估值和退出条件。
                5. 小仓位分散验证，避免借钱、满仓或追逐短期消息。

                当前为本地教学回答；仅供参考，不构成投资建议。
            """.trimIndent()

            listOf("市盈率", "PE", "pe").any { normalized.contains(it) } -> """
                **市盈率（PE）= 股价 ÷ 每股收益**，也可理解为公司市值与年度净利润之比。

                它适合与同一行业、相近商业模式和相同盈利周期的公司比较。亏损企业的 PE 通常没有直接意义，低 PE 也不一定代表低风险，还要结合增长、现金流和负债判断。

                当前为本地教学回答；仅供参考，不构成投资建议。
            """.trimIndent()

            listOf("分散", "风险管理", "控制风险").any { normalized.contains(it) } -> """
                分散风险不只是多买几只股票，还要避免持仓集中在同一行业、同一种风格或同一风险来源。可以先设单一标的与单一行业的仓位上限，并保留现金缓冲，定期按原定规则再平衡。

                当前为本地教学回答；仅供参考，不构成投资建议。
            """.trimIndent()

            else -> null
        }
    }
}
