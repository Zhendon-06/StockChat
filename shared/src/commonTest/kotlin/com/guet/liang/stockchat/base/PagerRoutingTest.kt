@file:Suppress("MagicNumber", "LongParameterList", "MaxLineLength")
package com.guet.liang.stockchat.base

import com.guet.liang.stockchat.data.toStockQuoteOrNull
import com.guet.liang.stockchat.model.StockQuote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PagerRoutingTest {
    @Test
    fun stockDetailParamsTrimOptionalKey() {
        val params = stockDetailRouteParams("sh600000", "  demo-key  ")
        assertEquals("sh600000", params.optString("symbol"))
        assertEquals("demo-key", params.optString("qwenApiKey"))
    }

    @Test
    fun stockDetailParamsCarryThePreviewQuoteWhenTheCallerHasOne() {
        val quote = StockQuote("贵州茅台", "600519", "沪市", "1520.00", "+12.00", "+0.80%", "now", true, listOf(1f, 2f), "", "")
        assertEquals(quote, stockDetailRouteParams("sh600519", preview = quote).optJSONObject(STOCK_DETAIL_PREVIEW_QUOTE_PARAM)?.toStockQuoteOrNull())
        assertNull(stockDetailRouteParams("sh600519").optJSONObject(STOCK_DETAIL_PREVIEW_QUOTE_PARAM))
    }

    @Test
    fun artifactParamsAlwaysEncodeStableIdentifier() {
        val params = artifactRouteParams(42L)
        assertEquals("42", params.optString("artifactId"))
        assertEquals("", params.optString("qwenApiKey"))
    }
}
