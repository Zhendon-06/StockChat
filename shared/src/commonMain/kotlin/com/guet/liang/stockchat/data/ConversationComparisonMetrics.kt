package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.ConversationStockComparisonRow
import com.guet.liang.stockchat.model.ConversationStockComparisonSnapshot
import com.guet.liang.stockchat.model.ConversationStockDataSource
import com.guet.liang.stockchat.model.ConversationTableArtifactSnapshot
import com.guet.liang.stockchat.model.ConversationTableRow
import com.guet.liang.stockchat.model.ConversationTableRowStatus
import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TencentMarketSnapshot

internal fun parseSummaryMetrics(summary: String): SummaryMetrics {
    return SummaryMetrics(
        previousClose = summary.metricValue("昨收"),
        open = summary.metricValue("今开"),
        high = summary.metricValue("最高"),
        low = summary.metricValue("最低"),
        volume = summary.metricValue("成交量"),
        volumeUnit = summary.metricUnit("成交量", setOf("手", "股")),
        amount = summary.metricValue("成交额"),
        amountUnit = summary.metricUnit("成交额", setOf("元", "万元", "亿元", "港元")),
        turnoverRate = summary.metricValue("换手率").removeSuffix("%"),
        priceEarningsRatio = summary.metricValue("市盈率").removeSuffix("倍"),
        amplitude = summary.metricValue("振幅").removeSuffix("%"),
    )
}

internal fun String.metricValue(label: String): String {
    return Regex("$label\\s*([^\\s，；;]+)")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
}

internal fun String.metricUnit(label: String, supportedUnits: Set<String>): String {
    val tail = substringAfter(label, "").trimStart()
    if (tail.isEmpty()) {
        return ""
    }
    val tokens = tail.substringBeforeAny('，', '；', ';').split(Regex("\\s+"))
    return tokens.getOrNull(1).orEmpty().takeIf(supportedUnits::contains).orEmpty()
}

internal fun String.substringBeforeAny(vararg delimiters: Char): String {
    val endIndex = delimiters.map { delimiter -> indexOf(delimiter) }
        .filter { index -> index >= 0 }
        .minOrNull()
        ?: length
    return take(endIndex)
}

internal fun ConversationStockComparisonRow.marketMetricSummary(): String {
    if (!hasQuote) {
        return "待获取最新行情"
    }
    return buildList {
        price.takeIf(String::isNotBlank)?.let { add("现价 $it") }
        changePercent.takeIf(String::isNotBlank)?.let { add("涨跌幅 $it") }
        open.takeIf(String::isNotBlank)?.let { add("今开 $it") }
        high.takeIf(String::isNotBlank)?.let { add("最高 $it") }
        low.takeIf(String::isNotBlank)?.let { add("最低 $it") }
        volume.takeIf(String::isNotBlank)?.let { add("成交量 $it$volumeUnit") }
        amount.takeIf(String::isNotBlank)?.let { add("成交额 $it$amountUnit") }
        turnoverRate.takeIf(String::isNotBlank)?.let { add("换手率 $it%") }
        priceEarningsRatio.takeIf(String::isNotBlank)?.let { add("市盈率 $it") }
        amplitude.takeIf(String::isNotBlank)?.let { add("振幅 $it%") }
        updatedAt.takeIf(String::isNotBlank)?.let { add(it) }
    }.joinToString("；").ifBlank { "已识别会话行情" }
}

/** Parsed comparison data shared by the generator helpers. */
internal data class SummaryMetrics(
    val previousClose: String,
    val open: String,
    val high: String,
    val low: String,
    val volume: String,
    val volumeUnit: String,
    val amount: String,
    val amountUnit: String,
    val turnoverRate: String,
    val priceEarningsRatio: String,
    val amplitude: String,
)
