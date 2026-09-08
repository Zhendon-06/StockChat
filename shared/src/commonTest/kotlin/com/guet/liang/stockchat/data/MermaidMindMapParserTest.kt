package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MermaidMindMapParserTest {

    @Test
    fun parsesGeneratedMindMapIntoNestedNodes() {
        val source = ConversationMindMapArtifactGenerator.generate(
            title = "市场复盘",
            messages = listOf(
                ChatMessage(
                    id = "question",
                    role = ChatRole.USER,
                    blocks = listOf(
                        AnswerBlock.Markdown(
                            source = "分析 600519.SH",
                            fallbackText = "",
                        ),
                    ),
                ),
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
        ).mermaidSource

        val root = assertNotNull(MermaidMindMapParser.parse(source))
        assertEquals("市场复盘", root.label)
        assertEquals(1, root.children.size)
        assertEquals("1. 分析 600519.SH", root.children.single().label)
        assertEquals(
            listOf(
                "洞察：关注估值与现金流。",
                "标的：600519.SH",
                "状态：已完成",
            ),
            root.children.single().children.map(MermaidMindMapNode::label),
        )
    }

    @Test
    fun rejectsMissingMindMapHeader() {
        assertEquals(null, MermaidMindMapParser.parse("root((市场复盘))"))
    }
}
