package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ConversationMindMapArtifactSnapshot
import com.guet.liang.stockchat.model.ConversationMindMapBranch
import com.guet.liang.stockchat.model.ConversationTableRowStatus

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal object ConversationMindMapArtifactGenerator {
    fun generate(
        title: String,
        messages: List<ChatMessage>,
    ): ConversationMindMapArtifactSnapshot {
        val tableSnapshot = ConversationTableArtifactGenerator.generate(title, messages)
        val baseTitle = tableSnapshot.title
            .removeSuffix(TABLE_ARTIFACT_TITLE_SUFFIX)
            .removeSuffix(MIND_MAP_ARTIFACT_TITLE_SUFFIX)
            .trim()
            .ifBlank { DEFAULT_ARTIFACT_TITLE }
        val branches = tableSnapshot.rows.map { row ->
            ConversationMindMapBranch(
                sequence = row.sequence,
                topic = row.userQuestion,
                insight = row.aiAnswerSummary,
                relatedInstrument = row.relatedInstrument,
                status = row.status,
            )
        }
        return ConversationMindMapArtifactSnapshot(
            title = "$baseTitle$MIND_MAP_ARTIFACT_TITLE_SUFFIX",
            sourceMessageCount = tableSnapshot.sourceMessageCount,
            mermaidSource = MermaidMindMapSourceGenerator.generate(
                title = baseTitle,
                branches = branches,
            ),
            branches = branches,
        )
    }

    private const val DEFAULT_ARTIFACT_TITLE = "当前会话"
    private const val TABLE_ARTIFACT_TITLE_SUFFIX = " · 产物表格"
    private const val MIND_MAP_ARTIFACT_TITLE_SUFFIX = " · 思维导图"
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal object MermaidMindMapSourceGenerator {
    fun generate(
        title: String,
        branches: List<ConversationMindMapBranch>,
    ): String {
        val lines = mutableListOf("mindmap", "  root((${title.toMermaidLabel(DEFAULT_ROOT_TITLE)}))")
        branches.forEach { branch ->
            lines += "    ${branch.sequence}. ${branch.topic.toMermaidLabel(DEFAULT_TOPIC_LABEL)}"
            lines += "      洞察：${branch.insight.toMermaidLabel(DEFAULT_INSIGHT_LABEL)}"
            if (branch.relatedInstrument.isNotBlank() && branch.relatedInstrument != UNKNOWN_INSTRUMENT) {
                lines += "      标的：${branch.relatedInstrument.toMermaidLabel(UNKNOWN_INSTRUMENT)}"
            }
            lines += "      状态：${branch.status.mermaidLabel()}"
        }
        return lines.joinToString("\n")
    }

    private fun String.toMermaidLabel(fallback: String): String {
        return trim()
            .replace(WHITESPACE_REGEX, " ")
            .replace(MERMAID_DELIMITER_REGEX, " ")
            .take(MAX_NODE_LENGTH)
            .trim()
            .ifBlank { fallback }
    }

    private fun ConversationTableRowStatus.mermaidLabel(): String {
        return when (this) {
            ConversationTableRowStatus.COMPLETED -> "已完成"
            ConversationTableRowStatus.GENERATING -> "生成中"
            ConversationTableRowStatus.FAILED -> "生成失败"
            ConversationTableRowStatus.WAITING -> "等待回答"
        }
    }

    private const val DEFAULT_ROOT_TITLE = "当前会话"
    private const val DEFAULT_TOPIC_LABEL = "未命名问题"
    private const val DEFAULT_INSIGHT_LABEL = "暂无 AI 摘要"
    private const val UNKNOWN_INSTRUMENT = "未识别"
    private const val MAX_NODE_LENGTH = 180
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val MERMAID_DELIMITER_REGEX = Regex("[\\[\\]{}()]|[|;]")
}
