package com.guet.liang.stockchat.data

import com.guet.liang.kuiklychart.finance.FinancialChartMath
import com.guet.liang.kuiklychart.finance.FinancialPoint
import com.guet.liang.kuiklychart.finance.financialNumber
import com.guet.liang.stockchat.model.ChartConclusion
import com.guet.liang.stockchat.model.ChartEvidenceReference
import com.guet.liang.stockchat.model.ChartEvidenceResolution
import com.guet.liang.stockchat.model.MarketPeriod
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal object ChartEvidenceResolver {
    /** Resolve against the actual rendered series. No nearest-date matching or clamping. */
    fun resolve(
        reference: ChartEvidenceReference?,
        symbol: String,
        period: MarketPeriod,
        sourceUpdatedAt: String,
        points: List<FinancialPoint>,
    ): ChartEvidenceResolution {
        val ref = reference ?: return ChartEvidenceResolution.Invalid("未提供可定位的结构化引用")
        val failure = referenceFailure(ref, symbol, period, sourceUpdatedAt) ?: seriesFailure(ref, points)
        if (failure != null) return ChartEvidenceResolution.Invalid(failure)
        val start = points.indexOfFirst { it.label == ref.startDate }
        val end = points.indexOfFirst { it.label == ref.endDate }
        val range = start..end
        val rangeFailure = when {
            start < 0 || end < start -> "引用日期不在已加载的交易记录中"
            ref.metric == "volume" && range.any { points[it].volume == null } -> "引用区间缺少成交量数据"
            ref.metric.startsWith("MA") && start + 1 < ref.metric.removePrefix("MA").toInt() -> "历史数据不足，无法计算引用均线"
            else -> null
        }
        val metric = METRIC_LABELS[ref.metric] ?: ref.metric
        return if (rangeFailure != null) ChartEvidenceResolution.Invalid(rangeFailure) else {
            ChartEvidenceResolution.Valid(range, "${ref.startDate} 至 ${ref.endDate} · $metric · ${range.count()} 根 K 线")
        }
    }

    private fun referenceFailure(ref: ChartEvidenceReference, symbol: String, period: MarketPeriod, updatedAt: String): String? = when {
        ref.symbol != symbol -> "引用标的与当前行情不一致"
        period.isIntraday || ref.period != period.key -> "引用周期与当前 K 线不一致"
        ref.sourceUpdatedAt.isBlank() || ref.sourceUpdatedAt != updatedAt -> "引用行情版本已变化，请重新生成解读"
        ref.metric !in SUPPORTED_METRICS -> "暂不支持该指标引用"
        else -> null
    }

    private fun seriesFailure(ref: ChartEvidenceReference, points: List<FinancialPoint>): String? = when {
        points.isEmpty() -> "暂无可定位的 K 线数据"
        points.any { !FinancialChartMath.valid(it) || !validMarketDate(it.label) } ||
            points.zipWithNext().any { (a, b) -> a.label >= b.label } -> "行情日期或数值异常，无法定位"
        !validMarketDate(ref.startDate) || !validMarketDate(ref.endDate) || ref.startDate > ref.endDate -> "引用日期格式或先后顺序无效"
        else -> null
    }

    private val METRIC_LABELS = mapOf("close" to "收盘价", "volume" to "成交量")
    private val SUPPORTED_METRICS = setOf("close", "volume", "MA5", "MA10", "MA20")
}

/** Malformed references keep their conclusion readable, but can never become a chart index. */
internal fun parseChartConclusions(array: JSONArray?): List<ChartConclusion> {
    if (array == null) return emptyList()
    return (0 until minOf(array.length(), MAX_CONCLUSIONS)).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val text = item.optString("text").trim().take(MAX_CONCLUSION_LENGTH)
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
    val start = (end - DEMO_LOOKBACK_OFFSET).coerceAtLeast(0)
    val first = points[start]
    val last = points[end]
    val movement = when {
        last.close > first.close -> "区间末收盘价较起点回升"
        last.close < first.close -> "区间末收盘价较起点回落"
        else -> "区间首尾收盘价持平"
    }
    val span = if (period == MarketPeriod.DAY) "${end - start + 1} 个交易日" else "${end - start + 1} 根${period.label}"
    return ChartConclusion(
        "${if (selectedIndex == null) "最近" else "截至所选日期的"}$span，$movement" +
            "（${financialNumber(first.close)} → ${financialNumber(last.close)}），不代表区间内单边变化。",
        ChartEvidenceReference(symbol, period.key, first.label, last.label, "close", sourceUpdatedAt),
    )
}

private const val MAX_CONCLUSIONS = 8
private const val MAX_CONCLUSION_LENGTH = 600
private const val DEMO_LOOKBACK_OFFSET = 4
