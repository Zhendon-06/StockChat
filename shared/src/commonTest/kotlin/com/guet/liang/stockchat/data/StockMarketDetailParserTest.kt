package com.guet.liang.stockchat.data

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.*

class StockMarketDetailParserTest {
    @Test fun indexTradingAmountsMustNeverBecomeAPointLevelAverage() {
        val rows = JSONArray().apply {
            put("0930 3935.55 3921979 6463694328.60")
            put("1130 3946.99 337120197 588660913365")
        }
        for (symbol in listOf("sh000001", "sz399001", "sz399006", "sh000300")) {
            val response = JSONObject().apply {
                put("code", 0)
                put("data", JSONObject().apply {
                    put(symbol, JSONObject().apply { put("data", JSONObject().apply {
                        put("date", "20260908"); put("data", rows)
                    }) })
                })
            }
            val chart = StockMarketDetailParser.chart(response, symbol, MarketPeriod.INTRADAY, 3932.7f)
            assertEquals(2, chart.points.size)
            assertTrue(chart.points.all { it.average == null })
            val range = com.guet.liang.kuiklychart.finance.FinancialChartMath.priceRange(chart.points, chart.previousClose, true)
            assertTrue(range.second - range.first < 100f, "成分股成交均价不得把指数纵轴扩大到数千点")
        }
        assertFalse(isMarketIndex("sh600371"))
        assertFalse(isMarketIndex("sz000001")) // 平安银行不是上证指数
    }

    @Test fun derivesMinuteVolumeAndVwapWithoutConnectingLunchOrFutureSession() {
        val rows = JSONArray().apply {
            put("0930 10.00 100 100000"); put("0931 11.00 150 155000")
            put("1130 11.00 200 210000"); put("1200 99.00 200 210000")
            put("1301 10.00 250 260000")
        }
        val points = StockMarketDetailParser.minutes(rows, "20260908", false)
        assertEquals(listOf(100f, 50f, 50f, 50f), points.map { it.volume })
        assertEquals(listOf(0f, 1f, 120f, 121f), points.map { it.slot })
        assertEquals(10.333333f, points[1].average!!, 0.0001f)
        assertEquals("09-08 13:01", points.last().label)
    }
    @Test fun malformedRowsAndCounterResetsDoNotInventVolume() {
        val rows = JSONArray().apply {
            put("0930 10 100 100000"); put("0931 NaN 200 200000")
            put("0932 11 50 50000"); put("0933 11 60 66000")
            put("0933 11 60 66000"); put("2460 10 70 70000")
        }
        val points = StockMarketDetailParser.minutes(rows, "20260908", false)
        assertEquals(3, points.size)
        assertNull(points[1].volume)
        assertEquals(10f, points.last().volume)
    }
    @Test fun hongKongUsesSharesAndLongerSession() {
        val rows = JSONArray().apply { put("1200 100 1000 100000"); put("1600 100 2000 200000") }
        val points = StockMarketDetailParser.minutes(rows, "20260908", true)
        assertEquals(listOf(150f, 330f), points.map { it.slot })
        assertEquals(100f, points.last().average)
    }
    @Test fun parsesActualWeeklyOhlcAndAdjustmentRatherThanRelabelingDailyData() {
        val rows = JSONArray().apply {
            put(JSONArray().apply { listOf("2026-09-04", "10", "11", "12", "9", "200").forEach { put(it) } })
        }
        val response = JSONObject().apply {
            put("code", 0); put("data", JSONObject().apply { put("sh600371", JSONObject().apply { put("qfqweek", rows) }) })
        }
        val chart = StockMarketDetailParser.chart(response, "sh600371", MarketPeriod.WEEK, 10f)
        assertEquals("前复权", chart.adjustment)
        assertEquals(12f, chart.points.single().high)
        assertEquals(11f, chart.points.single().close)
        assertTrue(StockMarketDetailParser.chart(response, "sh600371", MarketPeriod.MONTH, 10f).points.isEmpty())
    }
    @Test fun keepsSignedMainFlowAndCorrectOrderSizeColumns() {
        val response = JSONObject().apply {
            put("rc", 0); put("data", JSONObject().apply {
                put("klines", JSONArray().apply { put("2026-09-08,-30,20,10,-40,10"); put("2026-09-07,NaN,0,0,0,0") })
            })
        }
        val result = StockMarketDetailParser.capitalFlow(response).single()
        assertEquals(-30f, result.main)
        assertEquals(result.main, result.large + result.superLarge)
        assertEquals(20f, result.small)
    }
}
