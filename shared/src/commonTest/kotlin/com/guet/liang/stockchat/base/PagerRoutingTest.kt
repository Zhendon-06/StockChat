@file:Suppress("MagicNumber", "LongParameterList", "MaxLineLength")
package com.guet.liang.stockchat.base

import kotlin.test.Test
import kotlin.test.assertEquals

class PagerRoutingTest {
    @Test
    fun stockDetailParamsTrimOptionalKey() {
        val params = stockDetailRouteParams("sh600000", "  demo-key  ")
        assertEquals("sh600000", params.optString("symbol"))
        assertEquals("demo-key", params.optString("qwenApiKey"))
    }

    @Test
    fun artifactParamsAlwaysEncodeStableIdentifier() {
        val params = artifactRouteParams(42L)
        assertEquals("42", params.optString("artifactId"))
        assertEquals("", params.optString("qwenApiKey"))
    }
}
