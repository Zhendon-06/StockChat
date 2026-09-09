package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatSessionSummary

internal class FakeChatSessionRepository : ChatSessionRepository {
    val stored = linkedMapOf<String, List<ChatMessage>>()
    val archived = mutableSetOf<String>()
    val titles = mutableMapOf<String, String>()
    override fun loadSessions() = stored.keys.filterNot { it in archived }.map { summary(it) }
    override fun loadArchivedSessions() = stored.keys.filter { it in archived }.map { summary(it) }
    override fun loadMessages(sessionId: String) = stored[sessionId].orEmpty()
    override fun replaceMessages(sessionId: String, messages: List<ChatMessage>) { stored[sessionId] = messages }
    override fun clearSession(sessionId: String) { stored.remove(sessionId) }
    override fun archiveSession(sessionId: String): Boolean = stored.containsKey(sessionId) && archived.add(sessionId)
    override fun renameSession(sessionId: String, title: String) { titles[sessionId] = title }
    private fun summary(id: String) = ChatSessionSummary(id, titles[id] ?: id, 0L, id in archived)
}
