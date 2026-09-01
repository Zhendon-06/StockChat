package com.guet.liang.stockchat.model

internal data class ConversationMindMapBranch(
    val sequence: Int,
    val topic: String,
    val insight: String,
    val relatedInstrument: String,
    val status: ConversationTableRowStatus,
)

internal data class ConversationMindMapArtifactSnapshot(
    val title: String,
    val sourceMessageCount: Int,
    val branches: List<ConversationMindMapBranch>,
)

internal data class ConversationMindMapArtifact(
    val id: Long,
    val sessionId: String,
    val title: String,
    val sourceMessageCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val branches: List<ConversationMindMapBranch>,
)

internal data class ConversationMindMapArtifactSummary(
    val id: Long,
    val sessionId: String,
    val title: String,
    val branchCount: Int,
    val updatedAt: Long,
)
