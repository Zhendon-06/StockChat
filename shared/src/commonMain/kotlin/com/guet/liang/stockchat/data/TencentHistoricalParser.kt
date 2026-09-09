package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.TencentHistoricalCandle
import com.guet.liang.stockchat.model.TencentHistoricalPoint
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

internal fun tencentKlinePoints(securityData: JSONObject): List<Float> {
    val rows = securityData.optJSONArray("qfqday") ?: securityData.optJSONArray("day") ?: return emptyList()
    return (0 until rows.length()).mapNotNull { index ->
        rows.optJSONArray(index)?.optString(CLOSE_FIELD)?.toFloatOrNull()
    }
}

internal fun tencentKlineCandles(securityData: JSONObject): List<TencentHistoricalCandle> {
    val rows = securityData.optJSONArray("qfqday") ?: securityData.optJSONArray("day") ?: return emptyList()
    return (0 until rows.length()).mapNotNull { index -> rows.optJSONArray(index)?.let(::tencentCandle) }
}

private fun tencentCandle(row: JSONArray): TencentHistoricalCandle? {
    val date = row.optString(0).orEmpty().trim().replace('/', '-')
    val values = (1..VOLUME_FIELD).map { row.optString(it).orEmpty().trim().toFloatOrNull()?.takeIf(Float::isFinite) }
    if (date.isEmpty() || values.any { it == null }) return null
    val (open, close, high) = values.filterNotNull()
    val low = checkNotNull(values[LOW_VALUE_INDEX])
    val volume = checkNotNull(values[VOLUME_VALUE_INDEX])
    return TencentHistoricalCandle(date, open, close, high, low, volume)
}

internal fun tencentHistoricalPoints(securityData: JSONObject): List<TencentHistoricalPoint> {
    val rows = securityData.optJSONArray("qfqday")?.takeIf { it.length() > 0 }
        ?: securityData.optJSONArray("day")?.takeIf { it.length() > 0 } ?: return emptyList()
    return (0 until rows.length()).mapNotNull { index -> rows.optJSONArray(index)?.let(::tencentHistoricalPoint) }
        .distinctBy(TencentHistoricalPoint::date).sortedBy(TencentHistoricalPoint::date)
}

private fun tencentHistoricalPoint(row: JSONArray): TencentHistoricalPoint? {
    val date = row.optString(0).orEmpty().trim().replace('/', '-')
    val close = row.optString(CLOSE_FIELD).orEmpty().trim().toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
    return if (date.isNotEmpty() && close != null) TencentHistoricalPoint(date, close) else null
}

internal fun <T> sampleMarketPoints(points: List<T>, maxCount: Int): List<T> = when {
    maxCount <= 0 || points.isEmpty() -> emptyList()
    maxCount == 1 -> listOf(points.last())
    points.size <= maxCount -> points
    else -> List(maxCount) { index ->
        val sourceIndex = index * points.lastIndex.toFloat() / (maxCount - 1).toFloat()
        points[sourceIndex.toInt().coerceIn(points.indices)]
    }
}

private const val CLOSE_FIELD = 2
private const val VOLUME_FIELD = 5

private const val LOW_VALUE_INDEX = 3
private const val VOLUME_VALUE_INDEX = 4
