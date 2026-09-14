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

    /** Allows detail-page follow-ups to skip the independent stock/card request. */
    fun answer(
        question: String,
        history: List<ChatHistoryItem>,
        images: List<String>,
        model: String,
        attempt: Int,
        marketCardsEnabled: Boolean,
        callback: (ChatAnswer) -> Unit,
    ) = answer(question, history, images, model, attempt, callback)

}
