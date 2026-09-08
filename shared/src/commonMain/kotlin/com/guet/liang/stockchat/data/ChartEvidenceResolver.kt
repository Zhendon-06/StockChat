package com.guet.liang.stockchat.data

import com.guet.liang.kuiklychart.finance.FinancialChartMath
import com.guet.liang.kuiklychart.finance.FinancialPoint
import com.guet.liang.kuiklychart.finance.financialNumber
import com.guet.liang.stockchat.model.ChartConclusion
import com.guet.liang.stockchat.model.ChartEvidenceReference
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray

internal sealed class ChartEvidenceResolution {
    data class Valid(val indices: IntRange, val label: String) : ChartEvidenceResolution()
    data class Invalid(val reason: String) : ChartEvidenceResolution()
}

internal object ChartEvidenceResolver {
    /** Resolve against the actual rendered series. No nearest-date matching or clamping. */
    fun resolve(
        reference: ChartEvidenceReference?,
        symbol: String,
        period: MarketPeriod,
        sourceUpdatedAt: String,
        points: List<FinancialPoint>,
    ): ChartEvidenceResolution {
        fun invalid(reason: String) = ChartEvidenceResolution.Invalid(reason)
        val ref = reference ?: return invalid("未提供可定位的结构化引用")
        if (ref.symbol != symbol) return invalid("引用标的与当前行情不一致")
        if (period.isIntraday || ref.period != period.key) return invalid("引用周期与当前 K 线不一致")
        if (ref.sourceUpdatedAt.isBlank() || ref.sourceUpdatedAt != sourceUpdatedAt) return invalid("引用行情版本已变化，请重新生成解读")
        if (ref.metric !in setOf("close", "volume", "MA5", "MA10", "MA20")) return invalid("暂不支持该指标引用")
        if (points.isEmpty()) return invalid("暂无可定位的 K 线数据")
        if (points.any { !FinancialChartMath.valid(it) || !validDate(it.label) } ||
            points.zipWithNext().any { (a, b) -> a.label >= b.label }) return invalid("行情日期或数值异常，无法定位")
        if (!validDate(ref.startDate) || !validDate(ref.endDate) || ref.startDate > ref.endDate) return invalid("引用日期格式或先后顺序无效")
        val start = points.indexOfFirst { it.label == ref.startDate }
        val end = points.indexOfFirst { it.label == ref.endDate }
        if (start < 0 || end < start) return invalid("引用日期不在已加载的交易记录中")
        val range = start..end
        if (ref.metric == "volume" && range.any { points[it].volume == null }) return invalid("引用区间缺少成交量数据")
        if (ref.metric.startsWith("MA") && start + 1 < ref.metric.drop(2).toInt()) return invalid("历史数据不足，无法计算引用均线")
        val metric = when (ref.metric) { "close" -> "收盘价"; "volume" -> "成交量"; else -> ref.metric }
        return ChartEvidenceResolution.Valid(range, "${ref.startDate} 至 ${ref.endDate} · $metric · ${range.count()} 根 K 线")
    }

    private fun validDate(value: String): Boolean {
        if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value)) return false
        val year = value.take(4).toInt()
        val month = value.substring(5, 7).toInt()
        val day = value.takeLast(2).toInt()
        if (year < 1 || month !in 1..12) return false
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val days = when (month) { 2 -> if (leap) 29 else 28; 4, 6, 9, 11 -> 30; else -> 31 }
        return day in 1..days
    }
}

/** Malformed references keep their conclusion readable, but can never become a chart index. */
internal fun parseChartConclusions(array: JSONArray?): List<ChartConclusion> {
    if (array == null) return emptyList()
    return (0 until minOf(array.length(), 8)).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val text = item.optString("text").trim().take(600)
        if (text.isBlank()) return@mapNotNull null
        val ref = item.optJSONObject("reference")
        ChartConclusion(text, ref?.let {
            ChartEvidenceReference(it.optString("symbol"), it.optString("period"),
                it.optString("startDate"), it.optString("endDate"), it.optString("metric"), it.optString("sourceUpdatedAt"))
        })
    }
}

/** Local demonstration uses measured closes and the same reference contract as the remote model. */
internal fun marketDemoConclusion(
    symbol: String,
    period: MarketPeriod,
    sourceUpdatedAt: String,
    points: List<FinancialPoint>,
    selectedIndex: Int?,
): ChartConclusion? {
    if (period.isIntraday || points.isEmpty()) return null
    val end = selectedIndex?.takeIf { it in points.indices } ?: points.lastIndex
    val start = (end - 4).coerceAtLeast(0)
    val first = points[start]
    val last = points[end]
    val movement = when {
        last.close > first.close -> "区间末收盘价较起点回升"
        last.close < first.close -> "区间末收盘价较起点回落"
        else -> "区间首尾收盘价持平"
    }
    val span = if (period == MarketPeriod.DAY) "${end - start + 1} 个交易日" else "${end - start + 1} 根${period.label}"
    return ChartConclusion(
        "${if (selectedIndex == null) "最近" else "截至所选日期的"}$span，$movement（${financialNumber(first.close)} → ${financialNumber(last.close)}），不代表区间内单边变化。",
        ChartEvidenceReference(symbol, period.key, first.label, last.label, "close", sourceUpdatedAt),
    )
}
