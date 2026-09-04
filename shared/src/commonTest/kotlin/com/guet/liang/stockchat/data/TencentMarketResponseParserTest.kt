package com.guet.liang.stockchat.data

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TencentMarketResponseParserTest {
    @Test
    fun parsesQuoteAndDailyTrend() {
        val snapshot = assertNotNull(
            TencentMarketResponseParser.parseSnapshot(stockResponse(), "sh600519")
        )

        assertEquals("贵州茅台", snapshot.quote.name)
        assertEquals("600519", snapshot.quote.symbol)
        assertEquals("1297.40", snapshot.quote.price)
        assertEquals("+5.10", snapshot.quote.change)
        assertEquals("+0.39%", snapshot.quote.changePercent)
        assertEquals("腾讯行情 · 2026-08-28 16:15:00", snapshot.quote.updatedAt)
        assertEquals(listOf(1292.3f, 1297.4f), snapshot.quote.trendPoints)
        assertTrue(snapshot.quote.summary.contains("最高 1297.89"))
    }

    @Test
    fun parsesNegativeIndexQuote() {
        val snapshot = assertNotNull(
            TencentMarketResponseParser.parseSnapshot(indexResponse(), "sh000300")
        )

        assertEquals("-21.10", snapshot.quote.change)
        assertEquals("-0.46%", snapshot.quote.changePercent)
        assertEquals("沪市指数 · 腾讯行情", snapshot.quote.marketLabel)
        assertEquals(false, snapshot.quote.isPositive)
    }

    @Test
    fun parsesHongKongQuoteAndTimestamp() {
        val response = marketResponse(
            providerSymbol = "hk00700",
            name = "腾讯控股",
            code = "00700",
            price = "455.200",
            change = "7.400",
            changePercent = "1.65",
            trendKey = "day",
            trendPoints = listOf("447.800", "455.200"),
            timestamp = "2026/08/28 16:08:37",
        )

        val snapshot = assertNotNull(
            TencentMarketResponseParser.parseSnapshot(response, "hk00700")
        )

        assertEquals("港股 · 腾讯行情", snapshot.quote.marketLabel)
        assertEquals("腾讯行情 · 2026-08-28 16:08:37", snapshot.quote.updatedAt)
        assertEquals("股", snapshot.volumeUnit)
        assertEquals("港元", snapshot.amountUnit)
    }

    @Test
    fun parsesAndSamplesMinutePoints() {
        val minuteRows = JSONArray().apply {
            repeat(100) { index -> put("${930 + index} ${100 + index}.00 1 100.00") }
        }
        val response = JSONObject().apply {
            put("code", 0)
            put(
                "data",
                JSONObject().apply {
                    put(
                        "sh600519",
                        JSONObject().apply {
                            put("data", JSONObject().apply { put("data", minuteRows) })
                        }
                    )
                }
            )
        }

        val points = TencentMarketResponseParser.parseMinutePoints(response, "sh600519")

        assertEquals(80, points.size)
        assertEquals(100f, points.first())
        assertEquals(199f, points.last())
    }

    @Test
    fun parsesHistoricalPointsWithDatesAndSamplesToRequestedCount() {
        val rows = JSONArray().apply {
            repeat(150) { index ->
                val month = index / 30 + 1
                val day = index % 30 + 1
                put(
                    JSONArray().apply {
                        put("2026-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}")
                        put("${100 + index}.00")
                        put("${1000 + index}.50")
                    }
                )
            }
        }
        val response = JSONObject().apply {
            put("code", 0)
            put(
                "data",
                JSONObject().apply {
                    put(
                        "sh600519",
                        JSONObject().apply { put("qfqday", rows) },
                    )
                },
            )
        }

        val points = TencentMarketResponseParser.parseHistoricalPoints(
            response = response,
            providerSymbol = "sh600519",
            maxCount = 60,
        )

        assertEquals(60, points.size)
        assertEquals(TencentHistoricalPoint("2026-01-01", 1000.5f), points.first())
        assertEquals(TencentHistoricalPoint("2026-05-30", 1149.5f), points.last())
        assertTrue(points.zipWithNext().all { (previous, current) -> previous.date != current.date })
    }

    @Test
    fun parsesHistoricalPointsFromDayAndSkipsMalformedRows() {
        val rows = JSONArray().apply {
            put(JSONArray().apply {
                put("2026-08-27")
                put("10.00")
                put("10.50")
            })
            put(JSONArray().apply {
                put("2026-08-28")
                put("10.50")
                put("not-a-price")
            })
            put(JSONArray().apply {
                put("")
                put("10.75")
                put("11.00")
            })
            put(JSONArray().apply {
                put("2026-08-29")
                put("11.00")
                put("11.25")
            })
        }
        val response = JSONObject().apply {
            put("code", 0)
            put(
                "data",
                JSONObject().apply {
                    put(
                        "sh000300",
                        JSONObject().apply { put("day", rows) },
                    )
                },
            )
        }

        val points = TencentMarketResponseParser.parseHistoricalPoints(
            response = response,
            providerSymbol = "sh000300",
            maxCount = 20,
        )

        assertEquals(
            listOf(
                TencentHistoricalPoint("2026-08-27", 10.5f),
                TencentHistoricalPoint("2026-08-29", 11.25f),
            ),
            points,
        )
    }

    @Test
    fun ordersHistoricalPointsAscendingAndRemovesDuplicateDates() {
        val rows = JSONArray().apply {
            put(JSONArray().apply {
                put("2026/08/29")
                put("11.00")
                put("11.25")
            })
            put(JSONArray().apply {
                put("2026-08-28")
                put("10.50")
                put("10.75")
            })
            put(JSONArray().apply {
                put("2026-08-29")
                put("11.00")
                put("11.30")
            })
        }
        val response = JSONObject().apply {
            put("code", 0)
            put(
                "data",
                JSONObject().apply {
                    put(
                        "sh600519",
                        JSONObject().apply { put("qfqday", rows) },
                    )
                },
            )
        }

        val points = TencentMarketResponseParser.parseHistoricalPoints(
            response = response,
            providerSymbol = "sh600519",
            maxCount = 20,
        )

        assertEquals(
            listOf(
                TencentHistoricalPoint("2026-08-28", 10.75f),
                TencentHistoricalPoint("2026-08-29", 11.25f),
            ),
            points,
        )
    }

    @Test
    fun fallsBackToDayWhenQfqdayIsEmptyAndSkipsNonPositiveOrNonFinitePrices() {
        val dayRows = JSONArray().apply {
            put(JSONArray().apply {
                put("2026-08-27")
                put("10.00")
                put("10.50")
            })
            put(JSONArray().apply {
                put("2026-08-28")
                put("10.50")
                put("0")
            })
            put(JSONArray().apply {
                put("2026-08-29")
                put("10.50")
                put("-1")
            })
            put(JSONArray().apply {
                put("2026-08-30")
                put("10.50")
                put("NaN")
            })
            put(JSONArray().apply {
                put("2026-08-31")
                put("10.50")
                put("11.25")
            })
        }
        val response = JSONObject().apply {
            put("code", 0)
            put(
                "data",
                JSONObject().apply {
                    put(
                        "sh600519",
                        JSONObject().apply {
                            put("qfqday", JSONArray())
                            put("day", dayRows)
                        },
                    )
                },
            )
        }

        val points = TencentMarketResponseParser.parseHistoricalPoints(
            response = response,
            providerSymbol = "sh600519",
            maxCount = 20,
        )

        assertEquals(
            listOf(
                TencentHistoricalPoint("2026-08-27", 10.5f),
                TencentHistoricalPoint("2026-08-31", 11.25f),
            ),
            points,
        )
    }

    @Test
    fun decodesSearchResponse() {
        val response = "v_hint=\"sh~600519~\\u8d35\\u5dde\\u8305\\u53f0~gzmt~GP-A\";"

        val match = TencentMarketResponseParser.parseSearch(response).single()

        assertEquals("sh600519", match.providerSymbol)
        assertEquals("贵州茅台", match.name)
    }

    @Test
    fun decodesHongKongSearchResponse() {
        val response = "v_hint=\"hk~00700~\\u817e\\u8baf\\u63a7\\u80a1~txkg~GP\";"

        val match = TencentMarketResponseParser.parseSearch(response).single()

        assertEquals("hk00700", match.providerSymbol)
        assertEquals("腾讯控股", match.name)
    }

    @Test
    fun rejectsInvalidResponse() {
        assertNull(
            TencentMarketResponseParser.parseSnapshot(
                JSONObject().apply { put("code", 1) },
                "sh999999",
            )
        )
    }

    private fun stockResponse(): JSONObject {
        return marketResponse(
            providerSymbol = "sh600519",
            name = "贵州茅台",
            code = "600519",
            price = "1297.40",
            change = "5.10",
            changePercent = "0.39",
            trendKey = "qfqday",
            trendPoints = listOf("1292.30", "1297.40"),
        )
    }

    private fun indexResponse(): JSONObject {
        return marketResponse(
            providerSymbol = "sh000300",
            name = "沪深300",
            code = "000300",
            price = "4609.18",
            change = "-21.10",
            changePercent = "-0.46",
            trendKey = "day",
            trendPoints = listOf("4630.28", "4609.18"),
        )
    }

    private fun marketResponse(
        providerSymbol: String,
        name: String,
        code: String,
        price: String,
        change: String,
        changePercent: String,
        trendKey: String,
        trendPoints: List<String>,
        timestamp: String = "20260828161500",
    ): JSONObject {
        val quoteValues = MutableList<Any?>(44) { "" }.apply {
            this[1] = name
            this[2] = code
            this[3] = price
            this[4] = "1292.30"
            this[5] = "1289.00"
            this[6] = "16126"
            this[30] = timestamp
            this[31] = change
            this[32] = changePercent
            this[33] = "1297.89"
            this[34] = "1288.00"
            this[37] = "208601"
            this[38] = "0.13"
            this[39] = "19.92"
            this[43] = "0.77"
        }
        val quoteArray = JSONArray().apply { quoteValues.forEach(::put) }
        val trendRows = JSONArray().apply {
            trendPoints.forEachIndexed { index, close ->
                put(
                    JSONArray().apply {
                        put("2026-08-${27 + index}")
                        put("1290.00")
                        put(close)
                    }
                )
            }
        }
        return JSONObject().apply {
            put("code", 0)
            put(
                "data",
                JSONObject().apply {
                    put(
                        providerSymbol,
                        JSONObject().apply {
                            put(
                                "qt",
                                JSONObject().apply { put(providerSymbol, quoteArray) },
                            )
                            put(trendKey, trendRows)
                        }
                    )
                }
            )
        }
    }
}
