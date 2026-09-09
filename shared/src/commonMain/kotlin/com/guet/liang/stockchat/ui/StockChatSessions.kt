package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.replaceNativeText
import com.guet.liang.stockchat.model.ChatRole

// 会话管理：初始化/加载/刷新最近会话、新建、切换、归档与删除。

internal fun StockChatPage.initializeChatSessions() {
    val sessions = chatHistoryRepository.loadSessions()
    val allSessions = sessions + chatHistoryRepository.loadArchivedSessions()
    recentSessions.clear()
    sessions.forEach { session ->
        recentSessions.add(session)
    }
    allSessions.forEach { session ->
        session.id.substringAfterLast('_').toIntOrNull()?.let {
            sessionSequence = maxOf(sessionSequence, it)
        }
    }
    activeSessionId = nextSessionId()
}

internal fun StockChatPage.loadMessagesForActiveSession(): Boolean {
    messages.clear()
    chatHistoryRepository.loadMessages(activeSessionId).forEach { message ->
        messages.add(message)
        message.id.substringAfterLast('_').toIntOrNull()?.let {
            messageSequence = maxOf(messageSequence, it)
        }
    }
    return messages.isNotEmpty()
}

internal fun StockChatPage.refreshRecentSessions() {
    val sessions = chatHistoryRepository.loadSessions()
    val allSessions = sessions + chatHistoryRepository.loadArchivedSessions()
    recentSessions.clear()
    sessions.forEach { session ->
        recentSessions.add(session)
    }
    allSessions.forEach { session ->
        session.id.substringAfterLast('_').toIntOrNull()?.let {
            sessionSequence = maxOf(sessionSequence, it)
        }
    }
}

internal fun StockChatPage.nextSessionId(): String {
    sessionSequence += 1
    return "session_$sessionSequence"
}

internal fun StockChatPage.startNewChat() {
    cancelVoiceInput()
    persistChatHistory()
    requestToken += 1
    activeSessionId = nextSessionId()
    messages.clear()
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

internal fun StockChatPage.conversationTitle(): String {
    return messages.firstOrNull { it.role == ChatRole.USER }
        ?.let(::messageText)
        ?.lineSequence()
        ?.firstOrNull()
        ?.trim()
        ?.take(16)
        ?.ifBlank { null }
        ?: "新对话"
}

internal fun StockChatPage.deleteSession(sessionId: String) {
    if (sessionId.isBlank()) {
        return
    }
    closeRenameDialog()
    val deletingActiveSession = sessionId == activeSessionId
    persistChatHistory()
    requestToken += 1
    chatHistoryRepository.clearSession(sessionId)
    refreshRecentSessions()
    if (deletingActiveSession) {
        activeSessionId = recentSessions.firstOrNull()?.id ?: nextSessionId()
        messages.clear()
        resetMessageListScrollState()
        messageSequence = 0
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
    persistChatHistory()
    if (!chatHistoryRepository.archiveSession(sessionId)) {
        return
    }
    refreshRecentSessions()
    bridgeModule.toast("已归档，可在设置中查看")
}

internal fun StockChatPage.selectSession(sessionId: String) {
    if (sessionId == activeSessionId) {
        dispatchHome(
            StockChatHomeEvent.ConversationOpened(messages.isNotEmpty())
        )
        return
    }
    cancelVoiceInput()
    persistChatHistory()
    requestToken += 1
    activeSessionId = sessionId
    messages.clear()
    resetMessageListScrollState()
    messageSequence = 0
    isSending = false
    messageMenuTargetId = ""
    conversationMenuOpen = false
    val hasMessages = loadMessagesForActiveSession()
    dispatchHome(StockChatHomeEvent.ConversationOpened(hasMessages))
    updateTypingIndicatorTimer()
}
