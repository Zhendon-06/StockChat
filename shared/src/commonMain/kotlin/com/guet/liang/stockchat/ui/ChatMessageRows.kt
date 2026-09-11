package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.MessageState

// 消息行身份：决定两份消息快照能否复用同一个 vfor 行。
// 流式回答每个片段都会产生新的 ChatMessage，若按值比较，行会被整行重建，
// 行内已到达的行情卡片随之销毁再创建，用户按下时的节点在抬起前已不存在，点击就会失效。
// 因此流式行只按「结构」比较，正文交给页面上的 observable 实时驱动。

/** Provides stable row identity helpers for efficient streaming message rendering. */
internal object ChatMessageRows {
    /** 正在流式输出且已有可见内容的回答行：正文从页面 observable 读取，而不是行创建时的快照。 */
    fun isLiveRow(message: ChatMessage): Boolean =
        message.role == ChatRole.ASSISTANT && message.state == MessageState.GENERATING && message.blocks.isNotEmpty()

    /** 快照完全相同，或同一条流式回答只差正文文本时复用行。 */
    fun sameRow(old: ChatMessage, new: ChatMessage): Boolean =
        old == new || (isLiveRow(old) && isLiveRow(new) && rowShape(old) == rowShape(new))

    /** 当前正在流式输出正文的回答；没有在飞的回答时为 null。 */
    fun liveAnswer(messages: List<ChatMessage>): ChatMessage? = messages.lastOrNull(::isLiveRow)

    /** 行内第一段 Markdown 正文；流式期间它就是已收到的全部文本。 */
    fun markdownSource(message: ChatMessage): String =
        message.blocks.filterIsInstance<AnswerBlock.Markdown>().firstOrNull()?.source.orEmpty()

    private fun rowShape(message: ChatMessage): ChatMessage =
        message.copy(blocks = message.blocks.map { block -> if (block is AnswerBlock.Markdown) MARKDOWN_PLACEHOLDER else block })

    private val MARKDOWN_PLACEHOLDER = AnswerBlock.Markdown("", "")
}
