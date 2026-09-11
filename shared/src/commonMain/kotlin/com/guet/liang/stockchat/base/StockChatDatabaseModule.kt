package com.guet.liang.stockchat.base

import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 鸿蒙侧 SQLite 同步桥接：宿主用 relationalStore 执行 SQL，本模块只负责
 * 同步往返 JSON。Android / iOS 直接使用各自的 SQLDelight 驱动，不经过这里。
 *
 * 参数绑定与结果单元格统一编码为 `{"s": 文本}` / `{"n": 数值}` / `{}`(NULL)，
 * 避免依赖 JSON 引擎对 null 字面量的处理差异。
 */
internal class StockChatDatabaseModule : Module() {
    override fun moduleName(): String = MODULE_NAME

    fun ping(): Boolean = runCatching { call(METHOD_PING, null) }.isSuccess

    fun query(sql: String, arguments: JSONArray): JSONArray =
        call(METHOD_QUERY, request(sql, arguments)).optJSONArray("rows") ?: JSONArray()

    fun execute(sql: String, arguments: JSONArray): Long =
        call(METHOD_EXECUTE, request(sql, arguments)).optLong("changes")

    fun beginTransaction() {
        call(METHOD_BEGIN_TRANSACTION, null)
    }

    fun commit() {
        call(METHOD_COMMIT, null)
    }

    fun rollback() {
        call(METHOD_ROLLBACK, null)
    }

    private fun request(sql: String, arguments: JSONArray): JSONObject =
        JSONObject().apply {
            put("sql", sql)
            put("args", arguments)
        }

    private fun call(method: String, params: JSONObject?): JSONObject {
        val raw = toNative(false, method, params?.toString(), null, true).toString()
        val response = runCatching { JSONObject(raw) }.getOrNull()
            ?: throw IllegalStateException("数据库桥接返回了无法解析的结果：$method")
        if (response.optInt("ok") != 1) {
            throw IllegalStateException(response.optString("error").ifBlank { "数据库桥接调用失败：$method" })
        }
        return response
    }

    companion object {
        const val MODULE_NAME = "KRStockChatDatabaseModule"
        private const val METHOD_PING = "ping"
        private const val METHOD_QUERY = "query"
        private const val METHOD_EXECUTE = "execute"
        private const val METHOD_BEGIN_TRANSACTION = "beginTransaction"
        private const val METHOD_COMMIT = "commit"
        private const val METHOD_ROLLBACK = "rollback"
    }
}
