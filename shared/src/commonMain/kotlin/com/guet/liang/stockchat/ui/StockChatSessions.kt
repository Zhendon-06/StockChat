package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.replaceNativeText

// 会话管理：初始化/加载/刷新最近会话、新建、切换、归档与删除。

internal fun StockChatPage.initializeChatSessions() = sessionController.initialize()

internal fun StockChatPage.loadMessagesForActiveSession(): Boolean = sessionController.loadMessages()

internal fun StockChatPage.refreshRecentSessions() = sessionController.refresh()

internal fun StockChatPage.startNewChat() {
    cancelVoiceInput()
    sendController.invalidate()
    sessionController.newConversation()
    resetMessageListScrollState()
    inputText = ""
    resetInputLineMetrics()
    selectedImagePreviews.clear()
    selectedImages.clear()
    selectedImagePayloads.clear()
    selectedImageCount = 0
    isSending = false
    stickMessageListToBottom = true
    messageListNearBottom = true
    drawerOpen = false
    messageMenuTargetId = ""
    conversationMenuOpen = false
    modelMenuOpen = false
    if (inputRefReady) {
        inputRef.view?.replaceNativeText("")
    }
    dispatchHome(StockChatHomeEvent.NewConversationStarted)
    updateTypingIndicatorTimer()
}

internal fun StockChatPage.deleteSession(sessionId: String) {
    if (sessionId.isBlank()) {
        return
    }
    closeRenameDialog()
    val deletingActiveSession = sessionId == activeSessionId
    sendController.invalidate()
    sessionController.delete(sessionId)
    if (deletingActiveSession) {
        resetMessageListScrollState()
        inputText = ""
        resetInputLineMetrics()
        selectedImagePreviews.clear()
        selectedImages.clear()
        selectedImagePayloads.clear()
        selectedImageCount = 0
        isSending = false
        val hasMessages = loadMessagesForActiveSession()
        dispatchHome(StockChatHomeEvent.ConversationSynchronized(hasMessages))
        updateTypingIndicatorTimer()
    }
    bridgeModule.toast("已删除对话")
}

internal fun StockChatPage.archiveSession(sessionId: String) {
    if (sessionId.isBlank()) {
        return
    }
    if (!sessionController.archive(sessionId)) {
        return
    }
    refreshRecentSessions()
    bridgeModule.toast("已归档，可在设置中查看")
}

internal fun StockChatPage.selectSession(sessionId: String) {
    if (sessionId == activeSessionId) {
        dispatchHome(StockChatHomeEvent.ConversationOpened(messages.isNotEmpty()))
        return
    }
    cancelVoiceInput()
    sendController.invalidate()
    sessionController.select(sessionId)
    resetMessageListScrollState()
    isSending = false
    messageMenuTargetId = ""
    conversationMenuOpen = false
    val hasMessages = loadMessagesForActiveSession()
    dispatchHome(StockChatHomeEvent.ConversationOpened(hasMessages))
    updateTypingIndicatorTimer()
}
