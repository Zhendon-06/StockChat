package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ChatAnswer
import com.guet.liang.stockchat.model.ChatHistoryItem
import com.guet.liang.stockchat.model.StockDetailResult

internal interface StockChatDataSource {
    fun answer(
        question: String,
        history: List<ChatHistoryItem>,
        images: List<String>,
        model: String,
        attempt: Int,
        callback: (ChatAnswer) -> Unit,
    )

    fun stockDetail(symbol: String): StockDetailResult
}

internal object MockStockChatDataSource : StockChatDataSource {
    override fun answer(
        question: String,
        history: List<ChatHistoryItem>,
        images: List<String>,
        model: String,
        attempt: Int,
        callback: (ChatAnswer) -> Unit,
    ) {
        callback(ChatAnswer.Failure("当前未配置可用的数据服务。"))
    }

    override fun stockDetail(symbol: String): StockDetailResult {
        return StockDetailResult.Empty
    }
}
