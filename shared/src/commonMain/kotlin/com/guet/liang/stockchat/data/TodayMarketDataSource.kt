package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TodayMarketResult
import com.guet.liang.stockchat.model.TodayMarketSectorObservation
import com.guet.liang.stockchat.model.TodayMarketSnapshot
import com.tencent.kuikly.core.module.NetworkModule
import kotlin.math.abs
import kotlin.math.roundToInt

internal interface TodayMarketDataSource {
    fun load(callback: (TodayMarketResult) -> Unit)
}

internal class TencentTodayMarketDataSource(
    private val loadMarket: (SecuritiesQueryPlan, (MarketDataResult) -> Unit) -> Unit,
) : TodayMarketDataSource {
    constructor(networkModule: NetworkModule) : this(
        TencentMarketDataService(networkModule)::load,
    )

    constructor(marketDataService: TencentMarketDataService) : this(
        marketDataService::load,
    )

    override fun load(callback: (TodayMarketResult) -> Unit) {
        var finished = false
        fun finish(result: TodayMarketResult) {
            if (finished) {
                return
            }
            finished = true
            callback(result)
        }

        // 指数与样本股两批行情并发拉取，任一批失败只降级该批为演示补位，不拖垮整页
        var indexQuotes: Map<String, StockQuote>? = null
        var stockQuotes: Map<String, StockQuote>? = null
        fun maybeFinish() {
            val indices = indexQuotes ?: return
            val stocks = stockQuotes ?: return
            finish(TodayMarketResult.Success(buildSnapshot(indices, stocks)))
        }
        loadBatch(TODAY_MARKET_TARGETS) { quotes ->
            indexQuotes = quotes
            maybeFinish()
        }
        loadBatch(TODAY_MARKET_STOCK_TARGETS) { quotes ->
            stockQuotes = quotes
            maybeFinish()
        }
    }

    private fun loadBatch(
        targets: List<SecurityTarget>,
        callback: (Map<String, StockQuote>) -> Unit,
    ) {
        val plan = SecuritiesQueryPlan(
            intent = SecuritiesIntent.QUOTE,
            targets = targets,
            unresolvedTerms = emptyList(),
            needsTrend = true,
            needsIntraday = false,
            needsAi = false,
        )
        var done = false
        fun deliver(quotes: Map<String, StockQuote>) {
            if (done) {
                return
            }
            done = true
            callback(quotes)
        }
        runCatching {
            loadMarket(plan) { result ->
                deliver(
                    when (result) {
                        is MarketDataResult.Success -> result.snapshots.associate {
                            it.providerSymbol.lowercase() to it.quote
                        }
                        MarketDataResult.Empty,
                        is MarketDataResult.Failure -> emptyMap()
                    }
                )
            }
        }.onFailure {
            deliver(emptyMap())
        }
    }

    private fun buildSnapshot(
        indexBySymbol: Map<String, StockQuote>,
        stockBySymbol: Map<String, StockQuote>,
    ): TodayMarketSnapshot {
        val indices = TODAY_MARKET_TARGETS.mapNotNull { target ->
            indexBySymbol[target.providerSymbol.lowercase()]
                ?: DEMO_INDEX_QUOTES[target.providerSymbol]
        }
        val stockQuoteFor = { symbol: String ->
            stockBySymbol[symbol.lowercase()] ?: DEMO_STOCK_QUOTES[symbol]
        }
        val sampleStocks = TODAY_MARKET_STOCK_TARGETS
            .mapNotNull { stockQuoteFor(it.providerSymbol) }
            .sortedByDescending(::percentValue)
        val missingIndexCount = TODAY_MARKET_TARGETS.count {
            indexBySymbol[it.providerSymbol.lowercase()] == null
        }
        val missingStockCount = TODAY_MARKET_STOCK_TARGETS.count {
            stockBySymbol[it.providerSymbol.lowercase()] == null
        }
        val isDemo = missingIndexCount > 0 || missingStockCount > 0
        val allDemo = missingIndexCount == TODAY_MARKET_TARGETS.size &&
            missingStockCount == TODAY_MARKET_STOCK_TARGETS.size

        val sectors = TODAY_MARKET_SECTORS.map { spec ->
            val members = spec.symbols.mapNotNull(stockQuoteFor)
            val average = if (members.isEmpty()) {
                0.0
            } else {
                members.map(::percentValue).sum() / members.size
            }
            TodayMarketSectorObservation(
                name = spec.name,
                changeLabel = formatPercent(average),
                isPositive = average >= 0,
                members = members.joinToString(" · ", transform = StockQuote::name),
            )
        }.sortedByDescending { percentValue(it.changeLabel) }

        val advancingCount = indices.count { movement(it) > 0 }
        val decliningCount = indices.count { movement(it) < 0 }
        val unchangedCount = indices.size - advancingCount - decliningCount
        val mood = marketMood(advancingCount, decliningCount)

        val messagePrefix = when {
            allDemo -> "网络行情暂不可用，当前展示本地演示数据。"
            isDemo -> "部分行情暂不可用，缺失项以本地演示数据补位。"
            else -> ""
        }
        val leader = sampleStocks.firstOrNull()
        val laggard = sampleStocks.lastOrNull()
        val moverSummary = if (leader != null && laggard != null && leader !== laggard) {
            "样本股中${leader.name}表现最强（${leader.changePercent}），" +
                "${laggard.name}相对偏弱（${laggard.changePercent}）。"
        } else {
            ""
        }
        val strongestSector = sectors.firstOrNull()
        val sectorSummary = if (strongestSector != null) {
            "观察方向里${strongestSector.name}样本均值${strongestSector.changeLabel}居前。"
        } else {
            ""
        }
        val summary = listOf(
            messagePrefix,
            "覆盖 ${indices.size} 个主要指数：上涨 $advancingCount 个、" +
                "下跌 $decliningCount 个、持平 $unchangedCount 个，整体呈现${mood}。",
            moverSummary,
            sectorSummary,
            "板块与个股均基于固定样本，不是全市场排名。",
        ).filter(String::isNotBlank).joinToString(" ")

        return TodayMarketSnapshot(
            asOf = snapshotAsOf(indices, isDemo),
            indices = indices,
            advancingCount = advancingCount,
            decliningCount = decliningCount,
            unchangedCount = unchangedCount,
            mood = mood,
            sectors = sectors,
            sampleStocks = sampleStocks,
            summary = summary,
            isDemo = isDemo,
            sourceLabel = when {
                allDemo -> "本地演示数据（非实时）"
                isDemo -> "腾讯证券公开行情 + 本地演示补位"
                else -> "腾讯证券公开行情"
            },
        )
    }

    private fun snapshotAsOf(quotes: List<StockQuote>, isDemo: Boolean): String {
        if (isDemo && quotes.all { it.updatedAt.contains("非实时") }) {
            return "本地演示数据 · 非实时"
        }
        val timestamps = quotes.map(StockQuote::updatedAt).distinct()
        return when {
            timestamps.isEmpty() -> "时间未知"
            timestamps.size == 1 -> timestamps.first()
            else -> "各指数时间以卡片标注为准"
        }
    }

    private fun movement(quote: StockQuote): Int {
        val change = quote.change
            .replace(",", "")
            .replace("+", "")
            .trim()
            .toDoubleOrNull()
        if (change != null) {
            return change.compareTo(0.0)
        }
        return percentValue(quote).compareTo(0.0)
    }

    private fun percentValue(quote: StockQuote): Double = percentValue(quote.changePercent)

    private fun percentValue(percentText: String): Double {
        return percentText
            .replace("%", "")
            .replace("+", "")
            .replace(",", "")
            .trim()
            .toDoubleOrNull() ?: 0.0
    }

    private fun formatPercent(value: Double): String {
        val hundredths = (value * 100).roundToInt()
        val sign = when {
            hundredths > 0 -> "+"
            hundredths < 0 -> "-"
            else -> ""
        }
        val magnitude = abs(hundredths)
        return "$sign${magnitude / 100}.${(magnitude % 100).toString().padStart(2, '0')}%"
    }

    private fun marketMood(advancingCount: Int, decliningCount: Int): String {
        return when {
            advancingCount >= decliningCount + 2 -> "偏强"
            decliningCount >= advancingCount + 2 -> "偏弱"
            else -> "震荡"
        }
    }

    private data class SectorSpec(val name: String, val symbols: List<String>)

    companion object {
        internal val TODAY_MARKET_TARGETS = listOf(
            SecurityTarget("sh000001", "上证指数"),
            SecurityTarget("sz399001", "深证成指"),
            SecurityTarget("sz399006", "创业板指"),
            SecurityTarget("sh000300", "沪深300"),
        )

        // 固定样本股：覆盖四个常看方向，每方向两只代表性权重股
        internal val TODAY_MARKET_STOCK_TARGETS = listOf(
            SecurityTarget("sh600519", "贵州茅台"),
            SecurityTarget("sz000858", "五粮液"),
            SecurityTarget("sz300750", "宁德时代"),
            SecurityTarget("sz002594", "比亚迪"),
            SecurityTarget("sh600036", "招商银行"),
            SecurityTarget("sh601318", "中国平安"),
            SecurityTarget("sh688981", "中芯国际"),
            SecurityTarget("sz002475", "立讯精密"),
        )

        private val TODAY_MARKET_SECTORS = listOf(
            SectorSpec("消费白酒", listOf("sh600519", "sz000858")),
            SectorSpec("新能源车", listOf("sz300750", "sz002594")),
            SectorSpec("大金融", listOf("sh600036", "sh601318")),
            SectorSpec("硬科技", listOf("sh688981", "sz002475")),
        )

        private val DEMO_INDEX_QUOTES = mapOf(
            "sh000001" to StockQuote(
                name = "上证指数",
                symbol = "000001",
                marketLabel = "沪市指数 · 本地演示",
                price = "3,358.92",
                change = "+18.46",
                changePercent = "+0.55%",
                updatedAt = "本地演示数据 · 非实时",
                isPositive = true,
                trendPoints = listOf(3328f, 3336f, 3331f, 3345f, 3341f, 3352f, 3347f, 3358.92f),
                summary = "本地演示指数数据，仅用于展示今日市场卡片和走势。",
                aiInsight = "这是本地演示数据，不代表上证指数的真实点位或投资价值。",
            ),
            "sz399001" to StockQuote(
                name = "深证成指",
                symbol = "399001",
                marketLabel = "深市指数 · 本地演示",
                price = "10,642.18",
                change = "-36.21",
                changePercent = "-0.34%",
                updatedAt = "本地演示数据 · 非实时",
                isPositive = false,
                trendPoints = listOf(10690f, 10672f, 10681f, 10654f, 10661f, 10648f, 10652f, 10642.18f),
                summary = "本地演示指数数据，仅用于展示今日市场卡片和走势。",
                aiInsight = "这是本地演示数据，不代表深证成指的真实点位或投资价值。",
            ),
            "sz399006" to StockQuote(
                name = "创业板指",
                symbol = "399006",
                marketLabel = "深市指数 · 本地演示",
                price = "2,186.53",
                change = "-12.08",
                changePercent = "-0.55%",
                updatedAt = "本地演示数据 · 非实时",
                isPositive = false,
                trendPoints = listOf(2210f, 2202f, 2204f, 2192f, 2196f, 2189f, 2190f, 2186.53f),
                summary = "本地演示指数数据，仅用于展示今日市场卡片和走势。",
                aiInsight = "这是本地演示数据，不代表创业板指的真实点位或投资价值。",
            ),
            "sh000300" to StockQuote(
                name = "沪深300",
                symbol = "000300",
                marketLabel = "沪市指数 · 本地演示",
                price = "3,892.41",
                change = "+18.26",
                changePercent = "+0.47%",
                updatedAt = "本地演示数据 · 非实时",
                isPositive = true,
                trendPoints = listOf(3868f, 3874f, 3871f, 3882f, 3880f, 3887f, 3885f, 3892.41f),
                summary = "本地演示指数数据，仅用于展示今日市场卡片和走势。",
                aiInsight = "这是本地演示数据，不代表沪深300的真实点位或市场判断。",
            ),
        )

        private fun demoStockQuote(
            name: String,
            symbol: String,
            marketLabel: String,
            price: String,
            change: String,
            changePercent: String,
            trendPoints: List<Float>,
        ): StockQuote = StockQuote(
            name = name,
            symbol = symbol,
            marketLabel = "$marketLabel · 本地演示",
            price = price,
            change = change,
            changePercent = changePercent,
            updatedAt = "本地演示数据 · 非实时",
            isPositive = !change.startsWith("-"),
            trendPoints = trendPoints,
            summary = "本地演示个股数据，仅用于展示今日市场的样本观察。",
            aiInsight = "这是本地演示数据，不代表${name}的真实价格或投资价值。",
        )

        private val DEMO_STOCK_QUOTES = mapOf(
            "sh600519" to demoStockQuote(
                "贵州茅台", "600519", "沪市主板",
                "1,486.00", "+12.40", "+0.84%",
                listOf(1470f, 1474f, 1471f, 1479f, 1476f, 1482f, 1480f, 1486f),
            ),
            "sz000858" to demoStockQuote(
                "五粮液", "000858", "深市主板",
                "128.36", "+0.58", "+0.45%",
                listOf(127.6f, 127.9f, 127.7f, 128.2f, 128f, 128.3f, 128.1f, 128.36f),
            ),
            "sz300750" to demoStockQuote(
                "宁德时代", "300750", "创业板",
                "246.80", "-2.12", "-0.85%",
                listOf(249.5f, 248.8f, 249.1f, 247.9f, 248.2f, 247.3f, 247.6f, 246.8f),
            ),
            "sz002594" to demoStockQuote(
                "比亚迪", "002594", "深市主板",
                "108.92", "-0.44", "-0.40%",
                listOf(109.6f, 109.3f, 109.5f, 109f, 109.2f, 108.8f, 109f, 108.92f),
            ),
            "sh600036" to demoStockQuote(
                "招商银行", "600036", "沪市主板",
                "38.75", "+0.21", "+0.55%",
                listOf(38.4f, 38.5f, 38.45f, 38.6f, 38.55f, 38.7f, 38.65f, 38.75f),
            ),
            "sh601318" to demoStockQuote(
                "中国平安", "601318", "沪市主板",
                "52.18", "+0.16", "+0.31%",
                listOf(51.9f, 52f, 51.95f, 52.1f, 52.05f, 52.15f, 52.1f, 52.18f),
            ),
            "sh688981" to demoStockQuote(
                "中芯国际", "688981", "科创板",
                "86.40", "+1.05", "+1.23%",
                listOf(85.2f, 85.6f, 85.4f, 85.9f, 85.7f, 86.1f, 86f, 86.4f),
            ),
            "sz002475" to demoStockQuote(
                "立讯精密", "002475", "深市主板",
                "42.63", "-0.28", "-0.65%",
                listOf(43f, 42.9f, 42.95f, 42.75f, 42.85f, 42.7f, 42.75f, 42.63f),
            ),
        )
    }
}
