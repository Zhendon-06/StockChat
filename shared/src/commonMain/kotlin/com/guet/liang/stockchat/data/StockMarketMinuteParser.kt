package com.guet.liang.stockchat.data

import com.guet.liang.kuiklychart.finance.FinancialAxisLabel
import com.guet.liang.kuiklychart.finance.FinancialPoint
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray

internal fun parseMarketMinutes(rows: JSONArray?, date: String, hongKong: Boolean, isIndex: Boolean): List<FinancialPoint> {
    if (rows == null) return emptyList()
    val session = stockMarketSession(hongKong)
    val ordered = (0 until rows.length()).map { rows.optString(it).orEmpty().trim().split(Regex("\\s+")) }
        .filter { it.size >= MIN_MINUTE_FIELDS }.distinctBy { it[0] }.sortedBy { it[0] }
    var lastVolume = 0f
    return ordered.mapNotNull { row ->
        val minute = marketMinute(row, session) ?: return@mapNotNull null
        val volume = minute.cumulativeVolume?.takeIf { it >= lastVolume }?.minus(lastVolume)
        minute.cumulativeVolume?.let { lastVolume = it }
        val time = "${row[0].take(TIME_FIELD_WIDTH)}:${row[0].takeLast(TIME_FIELD_WIDTH)}"
        FinancialPoint(
            "${marketShortDate(date)} $time".trim(), minute.price, minute.price, minute.price, minute.price,
            volume, minute.average(hongKong, isIndex), minute.slot.toFloat(),
        )
    }
}

private fun marketMinute(row: List<String>, session: StockMarketSession): MarketMinute? {
    val time = row[0]
    if (time.length != COMPACT_TIME_LENGTH) return null
    val hour = time.take(TIME_FIELD_WIDTH).toIntOrNull()
    val minute = time.takeLast(TIME_FIELD_WIDTH).toIntOrNull()?.takeIf { it in 0 until MINUTES_PER_HOUR }
    val slot = hour?.let { h -> minute?.let { session.slot(h * MINUTES_PER_HOUR + it) } }
    val price = row[1].toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
    if (slot == null || price == null) return null
    val cumulative = row.getOrNull(VOLUME_FIELD)?.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
    val amount = row.getOrNull(AMOUNT_FIELD)?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    return MarketMinute(price, cumulative, amount, slot)
}

/** Validated minute observation before deriving incremental counters. */
private data class MarketMinute(val price: Float, val cumulativeVolume: Float?, val amount: Double?, val slot: Int) {
    fun average(hongKong: Boolean, isIndex: Boolean): Float? {
        if (isIndex) return null
        val volume = cumulativeVolume?.takeIf { it > 0f } ?: return null
        return amount?.let { (it / volume / if (hongKong) 1 else MAINLAND_LOT_SIZE).toFloat() }
            ?.takeIf { it.isFinite() && it > 0 }
    }
}

/** Market trading hours define chart slots and labels, including the lunch break. */
internal data class StockMarketSession(val length: Float, val morningEnd: Int, val close: Int, val labels: List<FinancialAxisLabel>) {
    fun slot(total: Int): Int? = when (total) {
        in OPEN_MINUTE..morningEnd -> total - OPEN_MINUTE
        in AFTERNOON_OPEN_MINUTE..close -> morningEnd - OPEN_MINUTE + total - AFTERNOON_OPEN_MINUTE
        else -> null
    }
}

internal fun stockMarketSession(hongKong: Boolean): StockMarketSession = if (hongKong) HONG_KONG_SESSION else MAINLAND_SESSION

internal fun marketShortDate(date: String): String = when {
    date.length == COMPACT_DATE_LENGTH -> date.drop(YEAR_LENGTH).chunked(TIME_FIELD_WIDTH).joinToString("-")
    date.length >= FORMATTED_DATE_LENGTH -> date.substring(FORMATTED_MONTH_START, FORMATTED_DATE_LENGTH)
    else -> date
}

private const val MIN_MINUTE_FIELDS = 2
private const val COMPACT_TIME_LENGTH = 4
private const val TIME_FIELD_WIDTH = 2
private const val MINUTES_PER_HOUR = 60
private const val VOLUME_FIELD = 2
private const val AMOUNT_FIELD = 3
private const val MAINLAND_LOT_SIZE = 100
private const val OPEN_MINUTE = 570
private const val AFTERNOON_OPEN_MINUTE = 780
private const val COMPACT_DATE_LENGTH = 8
private const val YEAR_LENGTH = 4
private const val FORMATTED_DATE_LENGTH = 10
private const val FORMATTED_MONTH_START = 5
private val MAINLAND_SESSION = StockMarketSession(
    240f, 690, 900,
    listOf(FinancialAxisLabel(0f, "09:30"), FinancialAxisLabel(120f, "11:30/13:00"), FinancialAxisLabel(240f, "15:00")),
)
private val HONG_KONG_SESSION = StockMarketSession(
    330f, 720, 960,
    listOf(FinancialAxisLabel(0f, "09:30"), FinancialAxisLabel(150f, "12:00/13:00"), FinancialAxisLabel(330f, "16:00")),
)
