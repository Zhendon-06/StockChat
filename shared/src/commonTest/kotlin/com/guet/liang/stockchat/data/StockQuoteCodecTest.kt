package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StockQuoteCodecTest {
    @Test
    fun quoteSurvivesJsonRoundTripThroughRouteParams() {
        val quote = StockQuote("贵州茅台", "600519", "沪市 · 腾讯行情", "1520.00", "+12.00", "+0.80%", "腾讯行情 · 09-10 14:30", true, listOf(1f, 2.5f, 2f), "总结", "洞察")
        val restored = JSONObject(quote.toJson().toString()).toStockQuoteOrNull()
        assertEquals(quote, restored)
    }

    @Test
    fun payloadWithoutIdentityIsRejected() {
        assertNull(JSONObject().apply { put("name", "贵州茅台") }.toStockQuoteOrNull())
        assertNull(JSONObject().apply { put("symbol", "600519") }.toStockQuoteOrNull())
    }
}
