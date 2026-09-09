@file:Suppress("MagicNumber", "LongParameterList", "MaxLineLength")
package com.guet.liang.stockchat.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TencentMarketDataServiceRequestTest {
    @Test
    fun historicalParamsEncodeProviderAndCount() {
        assertEquals("sh600519,day,,,60,qfq", historicalRequestParams("sh600519", 60).optString("param"))
    }

    @Test
    fun responseStatusRequiresSuccessfulFlagAndHttpCode() {
        assertTrue(isSuccessfulMarketResponse(true, null))
        assertTrue(isSuccessfulMarketResponse(true, 204))
        assertFalse(isSuccessfulMarketResponse(false, 200))
        assertFalse(isSuccessfulMarketResponse(true, 500))
    }
}
