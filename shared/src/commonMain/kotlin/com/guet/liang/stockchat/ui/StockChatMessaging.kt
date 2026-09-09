package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.ShareModule
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.replaceNativeText
import com.guet.liang.stockchat.data.StockChatShareContentBuilder
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.data.providerSymbolForQuote
import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatAnswer
import com.guet.liang.stockchat.model.ChatHistoryItem
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.MessageState
import com.guet.liang.stockchat.model.ModelCapability
import com.guet.liang.stockchat.model.ShareResult
import com.guet.liang.stockchat.model.VoiceInputState

// 消息收发：提问、回答落地、重试/重新生成、复制/分享/删除与历史上下文。

private const val MAX_HISTORY_TURNS = 6

private const val IMAGE_ONLY_QUESTION = "请分析我上传的图片"

internal fun StockChatPage.sendMessage(
    submittedText: String? = null,
    source: StockChatQuestionSource = StockChatQuestionSource.COMPOSER,
) {
    if (!homeState.canSubmitQuestion(source)) {
        return
    }
    if (isSending) {
        return
    }
    if (voiceInputState != VoiceInputState.IDLE) {
        bridgeModule.toast("请先结束语音输入")
        return
    }
    if (selectedModelId.isBlank()) {
        bridgeModule.toast("当前 Provider 暂无可用模型，请先在模型配置中获取模型")
        return
    }
    val attachedImages = selectedImagePreviews.toList()
    val attachedImagePayloads = selectedImagePayloads.toList()
    if (
        attachedImages.size != attachedImagePayloads.size ||
        attachedImagePayloads.any { !it.startsWith("data:image/") }
    ) {
        bridgeModule.toast("图片处理失败，请重新选择")
        return
    }
    if (
        attachedImages.isNotEmpty() &&
        ModelCapability.VISION !in selectedModel().capabilities
    ) {
        bridgeModule.toast("当前模型不支持图片理解，请切换到“视觉理解”模型后重试")
        return
    }
    val typedQuestion = (submittedText ?: inputText).trim()
    val question = typedQuestion.ifBlank {
        if (attachedImages.isNotEmpty()) IMAGE_ONLY_QUESTION else ""
    }
    if (question.isEmpty() && attachedImages.isEmpty()) {
        bridgeModule.toast("请输入问题，例如查行情、学炒股或问其他问题")
        return
    }
    dispatchHome(StockChatHomeEvent.QuestionCommitted(source))
    if (inputRefReady) {
        inputRef.view?.replaceNativeText("")
        inputRef.view?.blur()
    }
    inputText = ""
    resetInputLineMetrics()
    selectedImagePreviews.clear()
    selectedImages.clear()
    selectedImagePayloads.clear()
    selectedImageCount = 0
    voiceMode = false
    resetKeyboardState()
    isSending = true
    stickMessageListToBottom = true
    messageListNearBottom = true
    requestToken += 1
    val currentRequestToken = requestToken
    val userMessage = ChatMessage(
            id = nextMessageId(),
            role = ChatRole.USER,
            blocks = buildList {
                if (attachedImages.isNotEmpty()) {
                    add(
                        AnswerBlock.ImageGallery(
                            images = attachedImages,
                            requestImages = attachedImagePayloads,
                        )
                    )
                }
                add(AnswerBlock.Markdown(question, question))
            },
        )
    messages.add(userMessage)
    val answerId = nextMessageId()
    messages.add(
        ChatMessage(
            id = answerId,
            role = ChatRole.ASSISTANT,
            blocks = emptyList(),
            state = MessageState.GENERATING,
            retryQuestion = question,
        )
    )
    persistChatHistory()
    updateTypingIndicatorTimer()
    completeAnswer(answerId, question, 0, currentRequestToken)
}

internal fun StockChatPage.completeAnswer(
    messageId: String,
    question: String,
    attempt: Int,
    currentRequestToken: Int,
) {
    if (selectedModelId.isBlank()) {
        applyAnswer(
            messageId,
            question,
            attempt,
            ChatAnswer.Failure("当前 Provider 暂无可用模型，请先在模型配置中获取模型。"),
        )
        return
    }
    val history = conversationHistoryBefore(messageId)
    val attachedImages = imagesBeforeAnswer(messageId)
    val activeModel = selectedModel()
    if (
        attachedImages.isNotEmpty() &&
        ModelCapability.VISION !in activeModel.capabilities
    ) {
        applyAnswer(
            messageId,
            question,
            attempt,
            ChatAnswer.Failure(
                "当前模型 ${activeModel.displayName} 不支持图片理解，请切换到“视觉理解”模型后重试。"
            ),
        )
        return
    }
    runCatching {
        dataSource.answer(
            question,
            history,
            attachedImages,
            activeModel.id,
            attempt,
        ) response@{ answer ->
            if (currentRequestToken != requestToken) {
                return@response
            }
            applyAnswer(messageId, question, attempt, answer)
        }
    }.onFailure {
        if (currentRequestToken == requestToken) {
            applyAnswer(
                messageId,
                question,
                attempt,
                ChatAnswer.Failure("AI 服务暂时不可用，请稍后重试。"),
            )
        }
    }
}

internal fun StockChatPage.imagesBeforeAnswer(messageId: String): List<String> {
    val answerIndex = messages.indexOfFirst { it.id == messageId }
    if (answerIndex <= 0) {
        return emptyList()
    }
    for (index in (answerIndex - 1) downTo 0) {
        val message = messages[index]
        if (message.role == ChatRole.USER) {
            return message.blocks
                .filterIsInstance<AnswerBlock.ImageGallery>()
                .flatMap { it.requestImages }
                .filter { it.startsWith("data:image/") }
        }
    }
    return emptyList()
}

internal fun StockChatPage.conversationHistoryBefore(messageId: String): List<ChatHistoryItem> {
    val answerIndex = messages.indexOfFirst { it.id == messageId }
    if (answerIndex < 0) {
        return emptyList()
    }
    val historyItems = messages.take(answerIndex).mapNotNull { message ->
        val content = message.blocks.mapNotNull { block ->
            when (block) {
                is AnswerBlock.Markdown -> block.source.trim().ifEmpty { null }
                is AnswerBlock.MarketQuote -> providerSymbolForQuote(block.quote)?.let { providerSymbol ->
                    "[行情标的:$providerSymbol|${block.quote.name}] " +
                        "${block.quote.updatedAt}，现价 ${block.quote.price}，" +
                        "涨跌 ${block.quote.change}（${block.quote.changePercent}）"
                }
                is AnswerBlock.ImageGallery -> null
            }
        }.joinToString("\n\n").trim()
        if (content.isEmpty()) {
            null
        } else {
            ChatHistoryItem(message.role, content)
        }
    }
    val completedTurns = mutableListOf<ChatHistoryItem>()
    var pendingUserMessage: ChatHistoryItem? = null
    historyItems.forEach { item ->
        when (item.role) {
            ChatRole.USER -> pendingUserMessage = item
            ChatRole.ASSISTANT -> pendingUserMessage?.let { userMessage ->
                completedTurns += userMessage
                completedTurns += item
                pendingUserMessage = null
            }
        }
    }
    return completedTurns.takeLast(MAX_HISTORY_TURNS * 2)
}

internal fun StockChatPage.applyAnswer(
    messageId: String,
    question: String,
    attempt: Int,
    answer: ChatAnswer,
) {
    val index = messages.indexOfFirst { it.id == messageId }
    if (index < 0) {
        return
    }
    val previousMessage = messages[index]
    messages[index] = when (answer) {
        is ChatAnswer.Streaming -> previousMessage.copy(
            blocks = listOf(AnswerBlock.Markdown(answer.markdown, answer.markdown)),
            state = MessageState.GENERATING,
        )
        is ChatAnswer.Success -> ChatMessage(
            id = messageId,
            role = ChatRole.ASSISTANT,
            blocks = answer.blocks,
            // 保留原始提问，「重新生成」直接复用
            retryQuestion = question,
        )
        is ChatAnswer.Failure -> ChatMessage(
            id = messageId,
            role = ChatRole.ASSISTANT,
            blocks = emptyList(),
            state = MessageState.FAILED,
            retryQuestion = question,
            retryAttempt = attempt,
            errorMessage = answer.message,
        )
    }
    if (answer !is ChatAnswer.Streaming) {
        isSending = false
        persistChatHistory()
    }
    updateTypingIndicatorTimer()
}

internal fun StockChatPage.retryMessage(message: ChatMessage) {
    if (isSending || message.retryQuestion.isEmpty()) {
        return
    }
    val index = messages.indexOfFirst { it.id == message.id }
    if (index < 0) {
        return
    }
    isSending = true
    stickMessageListToBottom = true
    messageListNearBottom = true
    requestToken += 1
    val currentRequestToken = requestToken
    messages[index] = message.copy(
        blocks = emptyList(),
        state = MessageState.GENERATING,
        retryAttempt = message.retryAttempt + 1,
        errorMessage = "",
    )
    persistChatHistory()
    updateTypingIndicatorTimer()
    completeAnswer(
        message.id,
        message.retryQuestion,
        message.retryAttempt + 1,
        currentRequestToken,
    )
}

// 「重新生成」：已完成的回答也可重来；老会话可能没存 retryQuestion，回退到前一条用户消息
internal fun StockChatPage.regenerateMessage(message: ChatMessage) {
    if (isSending) {
        bridgeModule.toast("请等待当前回答完成")
        return
    }
    val index = messages.indexOfFirst { it.id == message.id }
    if (index < 0) {
        return
    }
    val question = message.retryQuestion.ifBlank {
        messages.take(index)
            .lastOrNull { it.role == ChatRole.USER }
            ?.let(::messageText)
            .orEmpty()
    }
    if (question.isBlank()) {
        bridgeModule.toast("找不到对应的提问，无法重新生成")
        return
    }
    val prepared = messages[index].copy(retryQuestion = question)
    messages[index] = prepared
    retryMessage(prepared)
}

internal fun StockChatPage.copyMessage(message: ChatMessage) {
    val content = messageText(message)
    if (content.isNotBlank()) {
        bridgeModule.copyToPasteboard(content)
        bridgeModule.toast("已复制回答")
    }
}

internal fun StockChatPage.shareMessage(message: ChatMessage) {
    val content = StockChatShareContentBuilder.fromMessage(message)
    if (content == null) {
        bridgeModule.toast("当前消息暂无可分享内容")
        return
    }
    val sharedSessionId = activeSessionId
    val sharedQuestion = sharedQuestion(message)
    val sharedRecord = StockChatSettingsStore.repository.recordSharedChat(
        sessionId = sharedSessionId,
        question = sharedQuestion,
        content = content,
    )
    acquireModule<ShareModule>(ShareModule.MODULE_NAME).share(content) { result ->
        when (result) {
            ShareResult.Success,
            ShareResult.Cancelled -> Unit
            is ShareResult.Failure -> {
                StockChatSettingsStore.repository.deleteSharedChat(sharedRecord.id)
                bridgeModule.toast(result.errorMessage)
            }
        }
    }
}

internal fun StockChatPage.sharedQuestion(message: ChatMessage): String {
    if (message.retryQuestion.isNotBlank()) {
        return message.retryQuestion.trim()
    }
    if (message.role == ChatRole.USER) {
        return messageText(message)
    }
    val messageIndex = messages.indexOfFirst { candidate -> candidate.id == message.id }
    return messages.take(messageIndex.coerceAtLeast(0))
        .lastOrNull { candidate -> candidate.role == ChatRole.USER }
        ?.let(::messageText)
        ?.ifBlank { null }
        ?: conversationTitle()
}

internal fun StockChatPage.copySelectedText(content: String) {
    if (content.isNotBlank()) {
        bridgeModule.copyToPasteboard(content)
        bridgeModule.toast("已复制选中文字")
    }
}

internal fun StockChatPage.deleteMessage(message: ChatMessage) {
    val index = messages.indexOfFirst { it.id == message.id }
    if (index < 0) {
        return
    }
    messages.removeAt(index)
    dispatchHome(
        StockChatHomeEvent.ConversationSynchronized(messages.isNotEmpty())
    )
    if (messages.isEmpty()) {
        // persistChatHistory 对空列表直接返回，这里显式清掉库里的旧消息
        chatHistoryRepository.clearSession(activeSessionId)
        refreshRecentSessions()
    } else {
        persistChatHistory()
    }
    bridgeModule.toast("已删除")
}

internal fun StockChatPage.messageText(message: ChatMessage): String {
    return message.blocks.mapNotNull { block ->
        when (block) {
            is AnswerBlock.Markdown -> block.fallbackText.ifBlank { block.source }
            is AnswerBlock.MarketQuote ->
                "${block.quote.name}（${block.quote.symbol}） ${block.quote.price} " +
                    "${block.quote.change} ${block.quote.changePercent}"
            is AnswerBlock.ImageGallery -> "图片附件 × ${block.images.size}"
        }.trim().ifBlank { null }
    }.joinToString("\n\n").trim()
}

internal fun StockChatPage.nextMessageId(): String {
    messageSequence += 1
    return "message_${activeSessionId}_$messageSequence"
}

internal fun StockChatPage.persistChatHistory() {
    if (activeSessionId.isBlank() || messages.isEmpty()) {
        return
    }
    chatHistoryRepository.replaceMessages(activeSessionId, messages)
    refreshRecentSessions()
}
