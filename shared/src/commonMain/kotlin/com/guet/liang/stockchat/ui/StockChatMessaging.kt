package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.ShareModule
import com.guet.liang.stockchat.base.StockChatShareContentBuilder
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.replaceNativeText
import com.guet.liang.stockchat.controller.ChatSubmission
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.ShareResult
import com.guet.liang.stockchat.model.VoiceInputState

// 消息收发：提问、回答落地、重试/重新生成、复制/分享/删除与历史上下文。

internal fun StockChatPage.sendMessage(submittedText: String? = null, source: StockChatQuestionSource = StockChatQuestionSource.COMPOSER) {
    if (!homeState.canSubmitQuestion(source) || isSending) return
    if (voiceInputState != VoiceInputState.IDLE) {
        bridgeModule.toast("请先结束语音输入")
        return
    }
    val submission = ChatSubmission(submittedText ?: inputText, selectedImagePreviews.toList(), selectedImagePayloads.toList())
    val error = sendController.validationError(submission)
    if (error != null) {
        bridgeModule.toast(error)
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
    prepareAnswerScroll()
    sendController.sendMessage(submission)
}

private fun StockChatPage.prepareAnswerScroll() {
    stickMessageListToBottom = true
    messageListNearBottom = true
}

internal fun StockChatPage.retryMessage(message: ChatMessage) {
    prepareAnswerScroll()
    sendController.retryMessage(message)
}

internal fun StockChatPage.regenerateMessage(message: ChatMessage) {
    prepareAnswerScroll()
    sendController.regenerateMessage(message)?.let { bridgeModule.toast(it) }
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
    val sharedRecord = settingsController.recordSharedChat(sessionId = sharedSessionId, question = sharedQuestion, content = content)
    acquireModule<ShareModule>(ShareModule.MODULE_NAME).share(content) { result ->
        when (result) {
            ShareResult.Success,
            ShareResult.Cancelled -> Unit
            is ShareResult.Failure -> {
                settingsController.deleteSharedChat(sharedRecord.id)
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
    return messages
        .take(messageIndex.coerceAtLeast(0))
        .lastOrNull { candidate -> candidate.role == ChatRole.USER }
        ?.let(::messageText)
        ?.ifBlank { null } ?: conversationTitle()
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
    sessionController.deleteMessage(message.id)
    dispatchHome(StockChatHomeEvent.ConversationSynchronized(messages.isNotEmpty()))
    bridgeModule.toast("已删除")
}

internal fun StockChatPage.messageText(message: ChatMessage): String = com.guet.liang.stockchat.base.messageText(message)

internal fun StockChatPage.persistChatHistory() = sessionController.persist()
