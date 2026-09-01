package com.guet.liang.stockchat.module

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import org.json.JSONObject

class KRShareModule : KuiklyRenderBaseModule() {

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        if (method != SHARE) {
            callback?.invoke(
                failureResult(
                    errorCode = "METHOD_NOT_FOUND",
                    errorMessage = "分享模块不支持该操作。",
                ),
            )
            return null
        }
        share(params, callback)
        return null
    }

    private fun share(params: String?, callback: KuiklyRenderCallback?) {
        val payload = runCatching { JSONObject(params ?: "{}") }.getOrElse {
            callback?.invoke(
                failureResult(
                    errorCode = "INVALID_SHARE_PAYLOAD",
                    errorMessage = "分享内容格式无效。",
                ),
            )
            return
        }
        val title = payload.optString("title").trim()
        val text = payload.optString("text").trim()
        val url = payload.optString("url").trim()
        val shareText = listOf(text, url)
            .filter(String::isNotBlank)
            .joinToString(separator = "\n\n")
            .ifBlank { title }
        if (shareText.isBlank()) {
            callback?.invoke(
                failureResult(
                    errorCode = "EMPTY_SHARE_CONTENT",
                    errorMessage = "没有可分享的内容。",
                ),
            )
            return
        }

        val hostActivity = activity
        if (hostActivity == null) {
            callback?.invoke(
                failureResult(
                    errorCode = "ACTIVITY_UNAVAILABLE",
                    errorMessage = "当前页面无法打开系统分享。",
                ),
            )
            return
        }
        hostActivity.runOnUiThread {
            if (hostActivity.isFinishing ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && hostActivity.isDestroyed)
            ) {
                callback?.invoke(
                    failureResult(
                        errorCode = "ACTIVITY_UNAVAILABLE",
                        errorMessage = "当前页面无法打开系统分享。",
                    ),
                )
                return@runOnUiThread
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                if (title.isNotBlank()) {
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TITLE, title)
                }
            }
            try {
                hostActivity.startActivity(Intent.createChooser(shareIntent, "分享"))
                callback?.invoke(successResult())
            } catch (error: ActivityNotFoundException) {
                callback?.invoke(
                    failureResult(
                        errorCode = "SHARE_TARGET_UNAVAILABLE",
                        errorMessage = "当前设备没有可用的分享应用。",
                    ),
                )
            } catch (error: RuntimeException) {
                callback?.invoke(
                    failureResult(
                        errorCode = "SHARE_FAILED",
                        errorMessage = error.message ?: "系统分享打开失败，请稍后重试。",
                    ),
                )
            }
        }
    }

    private fun successResult(): Map<String, Any> = mapOf(
        "success" to 1,
        "cancelled" to 0,
        "errorCode" to "",
        "errorMessage" to "",
    )

    private fun failureResult(errorCode: String, errorMessage: String): Map<String, Any> = mapOf(
        "success" to 0,
        "cancelled" to 0,
        "errorCode" to errorCode,
        "errorMessage" to errorMessage,
    )

    companion object {
        const val MODULE_NAME = "HRShareModule"
        private const val SHARE = "share"
    }
}
