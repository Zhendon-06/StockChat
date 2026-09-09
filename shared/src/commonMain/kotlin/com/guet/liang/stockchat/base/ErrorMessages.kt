package com.guet.liang.stockchat.base

/** Maps technical failures to stable, user-facing copy without exposing implementation details. */
internal fun Throwable.toUserMessage(fallback: String): String {
    val type = this::class.simpleName.orEmpty().lowercase()
    val detail = message.orEmpty().lowercase()
    return when {
        "timeout" in type || "timeout" in detail || "timed out" in detail -> "网络请求超时，请稍后重试。"
        "json" in type || "serialization" in type || "json" in detail || "parse" in detail -> "服务返回的数据格式暂不可用，请稍后重试。"
        "api key" in detail || "apikey" in detail || "密钥" in detail || "key missing" in detail -> "当前 Provider 没有可用 API Key，请先在模型配置页面填写后重试。"
        else -> fallback
    }
}
