package com.guet.liang.stockchat.base

import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager

/** 关闭当前 Kuikly 页面；各页面统一复用，避免每页各写一份同样的路由调用。 */
internal fun Pager.closePage() {
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
}

/** Opens a Kuikly route from one shared location so parameter packing stays consistent. */
internal fun Pager.openRoute(routeName: String, params: JSONObject = JSONObject()) {
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(routeName, params)
}

internal fun stockDetailRouteParams(symbol: String, qwenApiKey: String? = null): JSONObject =
    JSONObject().apply {
        put("symbol", symbol)
        qwenApiKey?.trim()?.takeIf(String::isNotBlank)?.let { put("qwenApiKey", it) }
    }

internal fun artifactRouteParams(id: Long, qwenApiKey: String? = null): JSONObject =
    JSONObject().apply {
        put("artifactId", id.toString())
        qwenApiKey?.trim()?.takeIf(String::isNotBlank)?.let { put("qwenApiKey", it) }
    }

/** Opens a stock detail route with the canonical symbol parameter. */
internal fun Pager.openStockDetail(symbol: String, qwenApiKey: String? = null) {
    openRoute("stock_detail", stockDetailRouteParams(symbol, qwenApiKey))
}

/** Opens a settings sub-page, preserving a single route parameter convention. */
internal fun Pager.openSettings(subPage: String? = null) {
    openRoute("stock_settings", JSONObject().apply { subPage?.takeIf(String::isNotBlank)?.let { put("subPage", it) } })
}

/** Opens an artifact page with its stable numeric identifier. */
internal fun Pager.openArtifact(routeName: String, id: Long, qwenApiKey: String? = null) {
    openRoute(routeName, artifactRouteParams(id, qwenApiKey))
}
