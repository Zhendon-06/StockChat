package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ConversationMindMapArtifactSnapshot
import com.guet.liang.stockchat.model.ConversationMindMapBranch

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
        return ConversationMindMapArtifactSnapshot(
            title = "$baseTitle$MIND_MAP_ARTIFACT_TITLE_SUFFIX",
            sourceMessageCount = tableSnapshot.sourceMessageCount,
            branches = tableSnapshot.rows.map { row ->
                ConversationMindMapBranch(
                    sequence = row.sequence,
                    topic = row.userQuestion,
                    insight = row.aiAnswerSummary,
                    relatedInstrument = row.relatedInstrument,
                    status = row.status,
                )
            },
        )
    }

    private const val DEFAULT_ARTIFACT_TITLE = "当前会话"
    private const val TABLE_ARTIFACT_TITLE_SUFFIX = " · 产物表格"
    private const val MIND_MAP_ARTIFACT_TITLE_SUFFIX = " · 思维导图"
}
