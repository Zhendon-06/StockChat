package com.guet.liang.stockchat.model

internal data class ShareContent(
    val title: String,
    val text: String,
    val url: String? = null,
)

internal sealed class ShareResult {
    data object Success : ShareResult()
    data object Cancelled : ShareResult()
    data class Failure(
        val errorCode: String,
        val errorMessage: String,
    ) : ShareResult()
}
