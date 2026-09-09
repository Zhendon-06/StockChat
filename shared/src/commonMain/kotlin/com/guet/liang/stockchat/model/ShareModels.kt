package com.guet.liang.stockchat.model

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class ShareContent(
    val title: String,
    val text: String,
    val url: String? = null,
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal sealed class ShareResult {
    data object Success : ShareResult()
    data object Cancelled : ShareResult()
    data class Failure(
        val errorCode: String,
        val errorMessage: String,
    ) : ShareResult()
}
