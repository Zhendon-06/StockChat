package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.data.StockChatDataSource
import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatAnswer
import com.guet.liang.stockchat.model.ChatHistoryItem
import com.guet.liang.stockchat.model.ChatModelOption
import com.guet.liang.stockchat.model.MessageState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatSendControllerTest {
    @Test
    fun streamingThenFailurePreservesRetryAndPersistsTerminalState() {
        val fixture = Fixture()
        fixture.send.sendMessage(ChatSubmission("行情"))
        fixture.source.callback(ChatAnswer.Streaming("正在分析"))
        assertTrue(fixture.send.isSending)
        assertEquals(MessageState.GENERATING, fixture.sessions.messages.last().state)
        fixture.source.callback(ChatAnswer.Failure("offline"))
        assertFalse(fixture.send.isSending)
        assertEquals("行情", fixture.sessions.messages.last().retryQuestion)
        assertEquals(fixture.sessions.messages, fixture.repository.stored[fixture.sessions.activeSessionId])
        fixture.send.retryMessage(fixture.sessions.messages.last())
        assertEquals(1, fixture.source.attempt)
    }

    @Test
    fun duplicateSubmissionAndStaleResponsesCannotChangeNewConversation() {
        val fixture = Fixture()
        fixture.send.sendMessage(ChatSubmission("行情"))
        fixture.send.sendMessage(ChatSubmission("重复"))
        assertEquals(1, fixture.source.requests)
        fixture.send.invalidate()
        fixture.sessions.newConversation()
        fixture.source.callback(ChatAnswer.Success(listOf(AnswerBlock.Markdown("旧回答", "旧回答"))))
        assertTrue(fixture.sessions.messages.isEmpty())
    }

    @Test
    fun malformedImagesAndEmptyQuestionsNeverSend() {
        val fixture = Fixture()
        fixture.send.sendMessage(ChatSubmission("   "))
        fixture.send.sendMessage(ChatSubmission("图片", listOf("preview")))
        assertEquals(0, fixture.source.requests)
        assertTrue(fixture.sessions.messages.isEmpty())
    }

    private class Fixture {
        val repository = FakeChatSessionRepository()
        val sessions = ChatSessionController(repository).apply { initialize() }
        val source = Source()
        val send = ChatSendController({ source }, sessions, { ChatModelOption("demo", "demo", "", "", "") })
    }

    private class Source : StockChatDataSource {
        var requests = 0
        var attempt = 0
        lateinit var callback: (ChatAnswer) -> Unit

        override fun answer(
            question: String,
            history: List<ChatHistoryItem>,
            images: List<String>,
            model: String,
            attempt: Int,
            callback: (ChatAnswer) -> Unit,
        ) {
            requests += 1
            this.attempt = attempt
            this.callback = callback
        }
    }
}
