package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ChatAnswer
import com.guet.liang.stockchat.model.ChatHistoryItem

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal interface StockChatDataSource {
    fun answer(
        question: String,
        history: List<ChatHistoryItem>,
        images: List<String>,
        model: String,
        attempt: Int,
        callback: (ChatAnswer) -> Unit,
    )

}
