package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatSessionControllerTest {
    @Test
    fun archivedSessionsAreIncludedWhenAllocatingNewIds() {
        val repository = FakeChatSessionRepository()
        repository.stored["session_8"] = listOf(message)
        repository.archived += "session_8"
        val controller = ChatSessionController(repository)
        controller.initialize()
        assertEquals("session_9", controller.activeSessionId)
        assertTrue(controller.recentSessions.isEmpty())
    }

    @Test
    fun switchingPersistsOldMessagesAndRestoresNewMessages() {
        val repository = FakeChatSessionRepository()
        val controller = ChatSessionController(repository)
        controller.initialize()
        controller.replaceMessages(listOf(message))
        controller.newConversation()
        assertEquals(listOf(message), repository.stored["session_1"])
        assertTrue(controller.messages.isEmpty())
        controller.select("session_1")
        assertEquals(listOf(message), controller.messages)
        assertEquals("行情", controller.conversationTitle)
    }

    @Test
    fun deletingLastMessageRemovesPersistedSession() {
        val repository = FakeChatSessionRepository()
        val controller = ChatSessionController(repository)
        controller.initialize()
        controller.replaceMessages(listOf(message))
        controller.persist()
        controller.deleteMessage(message.id)
        assertFalse(repository.stored.containsKey(controller.activeSessionId))
        assertTrue(controller.recentSessions.isEmpty())
    }

    companion object {
        private val message = ChatMessage("message_1", ChatRole.USER, listOf(AnswerBlock.Markdown("行情", "行情")))
    }
}
