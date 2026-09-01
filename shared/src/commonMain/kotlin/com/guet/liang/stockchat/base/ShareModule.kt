package com.guet.liang.stockchat.base

import com.guet.liang.stockchat.model.ShareContent
import com.guet.liang.stockchat.model.ShareResult
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

internal class ShareModule : Module() {
    override fun moduleName(): String = MODULE_NAME

    fun share(content: ShareContent, callback: (ShareResult) -> Unit) {
        val methodArgs = JSONObject().apply {
            put("title", content.title)
            put("text", content.text)
            content.url?.takeIf(String::isNotBlank)?.let { put("url", it) }
        }
        toNative(
            keepCallbackAlive = false,
            methodName = SHARE,
            param = methodArgs.toString(),
            callback = { result ->
                callback(
                    when {
                        result == null -> ShareResult.Failure(
                            errorCode = "NO_RESPONSE",
                            errorMessage = "分享暂时不可用，请稍后重试",
                        )
                        result.optInt("cancelled", 0) == 1 || result.optBoolean("cancelled") ->
                            ShareResult.Cancelled
                        result.optInt("success", 0) == 1 || result.optBoolean("success") ->
                            ShareResult.Success
                        else -> ShareResult.Failure(
                            errorCode = result.optString("errorCode"),
                            errorMessage = result.optString("errorMessage").ifBlank {
                                "分享失败，请稍后重试"
                            },
                        )
                    }
                )
            },
            syncCall = false,
        )
    }

    companion object {
        const val MODULE_NAME = "HRShareModule"
        private const val SHARE = "share"
    }
}
