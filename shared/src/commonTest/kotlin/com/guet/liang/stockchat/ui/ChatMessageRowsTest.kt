package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.MessageState
import com.guet.liang.stockchat.model.StockQuote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatMessageRowsTest {
    @Test
    fun streamingTextChangesReuseTheRowWhileCardsStayTheSame() {
        val first = streaming("正在", card)
        val next = streaming("正在分析茅台", card)
        assertTrue(ChatMessageRows.sameRow(first, next))
        assertEquals("正在分析茅台", ChatMessageRows.markdownSource(next))
    }

    @Test
    fun arrivingCardsAndDeliveryRebuildTheRow() {
        val textOnly = streaming("正在分析")
        val withCard = streaming("正在分析", card)
        assertFalse(ChatMessageRows.sameRow(textOnly, withCard))
        val delivered = withCard.copy(state = MessageState.DELIVERED)
        assertFalse(ChatMessageRows.sameRow(withCard, delivered))
        assertFalse(ChatMessageRows.isLiveRow(delivered))
    }

    @Test
    fun waitingAnswerAndOtherMessagesCompareByValue() {
        val waiting = ChatMessage("a1", ChatRole.ASSISTANT, emptyList(), MessageState.GENERATING)
        assertFalse(ChatMessageRows.isLiveRow(waiting))
        assertFalse(ChatMessageRows.sameRow(waiting, streaming("你好")))
        val user = ChatMessage("u1", ChatRole.USER, listOf(AnswerBlock.Markdown("行情", "行情")))
        assertTrue(ChatMessageRows.sameRow(user, user.copy()))
        assertFalse(ChatMessageRows.sameRow(user, user.copy(blocks = listOf(AnswerBlock.Markdown("走势", "走势")))))
    }

    @Test
    fun liveAnswerIsTheStreamingAssistantMessageOnly() {
        val user = ChatMessage("u1", ChatRole.USER, listOf(AnswerBlock.Markdown("行情", "行情")))
        assertNull(ChatMessageRows.liveAnswer(listOf(user, streaming("完成", card).copy(state = MessageState.DELIVERED))))
        val live = streaming("正在", card)
        assertEquals(live, ChatMessageRows.liveAnswer(listOf(user, live)))
    }

    private fun streaming(markdown: String, vararg extra: AnswerBlock) =
        ChatMessage("a1", ChatRole.ASSISTANT, listOf(AnswerBlock.Markdown(markdown, markdown)) + extra, MessageState.GENERATING, retryQuestion = "行情")

    private val card = AnswerBlock.MarketQuote(StockQuote("贵州茅台", "600519", "沪市", "1", "0", "0%", "now", true, emptyList(), "", ""))
}
