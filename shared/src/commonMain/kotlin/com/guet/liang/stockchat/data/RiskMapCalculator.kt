package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.RiskMapSnapshot
import kotlin.math.abs

private const val NEUTRAL_MOVE_THRESHOLD = 0.2f

/** Pure, snapshot-based portfolio exposure summary used by the risk map page. */
internal fun buildRiskMapSnapshot(quotes: List<StockQuote>): RiskMapSnapshot {
    val rising = quotes.count { it.isPositive && parsePercent(it.changePercent) > 0f }
    val falling = quotes.count { !it.isPositive && parsePercent(it.changePercent) < 0f }
    val unchanged = (quotes.size - rising - falling).coerceAtLeast(0)
    val changes = quotes.map { parsePercent(it.changePercent) }
    val average = changes.average().toFloat().takeIf { changes.isNotEmpty() } ?: 0f
    val largest = quotes.maxByOrNull { abs(parsePercent(it.changePercent)) }
    val concentration = when {
        quotes.isEmpty() -> "暂无自选数据"
        quotes.size == 1 -> "单标的暴露"
        rising == quotes.size || falling == quotes.size -> "方向高度集中"
        else -> "涨跌分布分散"
    }
    val headline = when {
        quotes.isEmpty() -> "先收藏行情卡片，再查看组合暴露。"
        average > NEUTRAL_MOVE_THRESHOLD -> "自选组合整体偏强，但仍需关注单一标的波动。"
        average < -NEUTRAL_MOVE_THRESHOLD -> "自选组合整体偏弱，优先核对风险与数据时点。"
        else -> "自选组合涨跌分化，适合结合详情页逐只核验。"
    }
    return RiskMapSnapshot(
        total = quotes.size,
        rising = rising,
        falling = falling,
        unchanged = unchanged,
        averageChange = average,
        largestMove = largest,
        concentrationLabel = concentration,
        headline = headline,
    )
}

private fun parsePercent(value: String): Float =
    value.trim().removeSuffix("%").replace(",", "").toFloatOrNull() ?: 0f
