package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklychart.finance.FinancialInterval
import com.guet.liang.kuiklychart.finance.FinancialPoint
import com.guet.liang.stockchat.model.StockPredictionHistoryPoint
import com.guet.liang.stockchat.model.StockPredictionPoint

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class PredictionChartData(
    val points: List<FinancialPoint>,
    val forecastStart: Int?,
    val intervals: Map<Int, FinancialInterval>,
)

/** Keep true dates and units; never morph intraday samples into daily history. */
internal fun predictionChartData(history: List<StockPredictionHistoryPoint>, forecast: List<StockPredictionPoint>): PredictionChartData {
    val historical = history.filter { it.close.isFinite() && it.close > 0f }.distinctBy { it.timestamp }.sortedBy { it.timestamp }
    val future =
        if (historical.isEmpty()) emptyList()
        else
            forecast
                .filter {
                    it.predictedPrice.isFinite() &&
                        it.predictedPrice > 0f &&
                        (historical.lastOrNull()?.timestamp?.let { last -> it.timestamp > last } != false)
                }
                .distinctBy { it.timestamp }
                .sortedBy { it.timestamp }
    val points =
        historical.map { FinancialPoint(it.timestamp, it.close, it.close, it.close, it.close) } +
            future.map { FinancialPoint(it.timestamp, it.predictedPrice, it.predictedPrice, it.predictedPrice, it.predictedPrice) }
    val bounds = future.mapIndexedNotNull { index, point -> predictionInterval(point)?.let { (historical.size + index) to it } }.toMap()
    return PredictionChartData(points, historical.size.takeIf { future.isNotEmpty() && historical.isNotEmpty() }, bounds)
}

private fun predictionInterval(point: StockPredictionPoint): FinancialInterval? {
    val lower = point.lowerBound ?: return null
    val upper = point.upperBound ?: return null
    if (!lower.isFinite() || !upper.isFinite()) return null
    if (lower <= 0 || lower > point.predictedPrice || upper < point.predictedPrice) return null
    return FinancialInterval(lower, upper)
}
