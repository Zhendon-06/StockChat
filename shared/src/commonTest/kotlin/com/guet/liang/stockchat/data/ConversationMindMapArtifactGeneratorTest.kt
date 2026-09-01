package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.ConversationTableRowStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationMindMapArtifactGeneratorTest {

    @Test
    fun generatesCompletedBranchFromQuestionAndAnswer() {
        val snapshot = ConversationMindMapArtifactGenerator.generate(
            title = "市场复盘",
            messages = listOf(
                userMessage("question", "分析 600519.SH"),
                ChatMessage(
                    id = "answer",
                    role = ChatRole.ASSISTANT,
                    blocks = listOf(
                        AnswerBlock.Markdown(
                            source = "关注估值与现金流。",
                            fallbackText = "",
                        ),
                    ),
                ),
            ),
        )

        assertEquals("市场复盘 · 思维导图", snapshot.title)
        assertEquals(2, snapshot.sourceMessageCount)
        assertEquals(1, snapshot.branches.size)
        with(snapshot.branches.single()) {
            assertEquals(1, sequence)
            assertEquals("分析 600519.SH", topic)
            assertEquals("关注估值与现金流。", insight)
            assertEquals("600519.SH", relatedInstrument)
            assertEquals(ConversationTableRowStatus.COMPLETED, status)
        }
    }

    @Test
    fun marksQuestionWithoutAssistantMessageAsWaiting() {
        val snapshot = ConversationMindMapArtifactGenerator.generate(
            title = "待回答",
            messages = listOf(userMessage("question", "分析 600519.SH")),
        )

        with(snapshot.branches.single()) {
            assertEquals("分析 600519.SH", topic)
            assertEquals("等待 AI 回答", insight)
            assertEquals(ConversationTableRowStatus.WAITING, status)
        }
    }

    @Test
    fun appendsMindMapTitleSuffixOnceAndDefaultsBlankTitle() {
        val suffixed = ConversationMindMapArtifactGenerator.generate(
            title = "  会话复盘   ·  思维导图  ",
            messages = emptyList(),
        )
        val blank = ConversationMindMapArtifactGenerator.generate(
            title = " \n\t ",
            messages = emptyList(),
        )

        assertEquals("会话复盘 · 思维导图", suffixed.title)
        assertEquals("当前会话 · 思维导图", blank.title)
    }

    private fun userMessage(id: String, text: String): ChatMessage {
        return ChatMessage(
            id = id,
            role = ChatRole.USER,
            blocks = listOf(AnswerBlock.Markdown(source = text, fallbackText = "")),
        )
    }
}
