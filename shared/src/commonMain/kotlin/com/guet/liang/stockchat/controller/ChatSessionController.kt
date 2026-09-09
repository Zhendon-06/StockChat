package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.base.messageText
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.ChatSessionSummary

/** Persistence contract permits deterministic session tests without a database or Pager. */
internal interface ChatSessionRepository {
    fun loadSessions(): List<ChatSessionSummary>

    fun loadArchivedSessions(): List<ChatSessionSummary>

    fun loadMessages(sessionId: String): List<ChatMessage>

    fun replaceMessages(sessionId: String, messages: List<ChatMessage>)

    fun clearSession(sessionId: String)

    fun archiveSession(sessionId: String): Boolean

    fun renameSession(sessionId: String, title: String)
}

/** Owns session identity and messages, publishing snapshots for Kuikly observables. */
internal class ChatSessionController(
    private val repository: ChatSessionRepository,
    private val onChanged: (ChatSessionController) -> Unit = {},
) {
    var activeSessionId = ""
        private set

    var messages: List<ChatMessage> = emptyList()
        private set

    var recentSessions: List<ChatSessionSummary> = emptyList()
        private set

    private var sessionSequence = 0
    private var messageSequence = 0

    fun initialize() {
        refresh()
        activeSessionId = nextSessionId()
        publish()
    }

    fun refresh() {
        recentSessions = repository.loadSessions()
        val all = recentSessions + repository.loadArchivedSessions()
        all.mapNotNull { it.id.substringAfterLast('_').toIntOrNull() }.maxOrNull()?.let { sessionSequence = maxOf(sessionSequence, it) }
        publish()
    }

    fun newConversation() {
        persist()
        activeSessionId = nextSessionId()
        messageSequence = 0
        messages = emptyList()
        publish()
    }

    fun select(sessionId: String) {
        if (sessionId != activeSessionId) {
            persist()
            activeSessionId = sessionId
            loadMessages()
        }
    }

    fun loadMessages(): Boolean {
        messages = repository.loadMessages(activeSessionId)
        messageSequence = messages.mapNotNull { it.id.substringAfterLast('_').toIntOrNull() }.maxOrNull() ?: 0
        publish()
        return messages.isNotEmpty()
    }

    fun delete(sessionId: String) {
        if (sessionId.isBlank()) return
        persist()
        repository.clearSession(sessionId)
        refresh()
        if (sessionId == activeSessionId) {
            activeSessionId = recentSessions.firstOrNull()?.id ?: nextSessionId()
            loadMessages()
        }
    }

    fun archive(sessionId: String): Boolean {
        persist()
        val archived = sessionId.isNotBlank() && repository.archiveSession(sessionId)
        if (archived) refresh()
        return archived
    }

    fun rename(sessionId: String, title: String) {
        repository.renameSession(sessionId, title)
        refresh()
    }

    fun replaceMessages(next: List<ChatMessage>) {
        messages = next.toList()
        publish()
    }

    fun deleteMessage(messageId: String) {
        replaceMessages(messages.filterNot { it.id == messageId })
        if (messages.isEmpty()) {
            repository.clearSession(activeSessionId)
            refresh()
        } else persist()
    }

    fun persist() {
        if (activeSessionId.isNotBlank() && messages.isNotEmpty()) {
            repository.replaceMessages(activeSessionId, messages)
            refresh()
        }
    }

    val conversationTitle: String
        get() =
            messages
                .firstOrNull { it.role == ChatRole.USER }
                ?.let(::messageText)
                ?.lineSequence()
                ?.firstOrNull()
                ?.trim()
                ?.take(TITLE_LENGTH)
                ?.ifBlank { null } ?: "新对话"

    fun nextMessageId(): String = "message_${activeSessionId}_${++messageSequence}"

    private fun nextSessionId(): String = "session_${++sessionSequence}"

    private fun publish() = onChanged(this)

    companion object {
        private const val TITLE_LENGTH = 16
    }
}
