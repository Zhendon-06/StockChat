package com.guet.liang.stockchat.model

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class ConversationMindMapBranch(
    val sequence: Int,
    val topic: String,
    val insight: String,
    val relatedInstrument: String,
    val status: ConversationTableRowStatus,
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class ConversationMindMapArtifactSnapshot(
    val title: String,
    val sourceMessageCount: Int,
    val mermaidSource: String,
    val branches: List<ConversationMindMapBranch>,
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class ConversationMindMapArtifact(
    val id: Long,
    val sessionId: String,
    val title: String,
    val sourceMessageCount: Int,
    val mermaidSource: String,
    val createdAt: Long,
    val updatedAt: Long,
    val branches: List<ConversationMindMapBranch>,
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class ConversationMindMapArtifactSummary(
    val id: Long,
    val sessionId: String,
    val title: String,
    val branchCount: Int,
    val updatedAt: Long,
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class MermaidMindMapNode(
    val label: String,
    val children: List<MermaidMindMapNode>,
)
