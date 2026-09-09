package com.guet.liang.stockchat.model

internal enum class ConversationTableRowStatus(
    val label: String,
) {
    COMPLETED("已完成"),
    GENERATING("生成中"),
    FAILED("生成失败"),
    WAITING("等待回答"),
}

internal data class ConversationTableRow(
    val sequence: Int,
    val userQuestion: String,
    val aiAnswerSummary: String,
    val relatedInstrument: String,
    val status: ConversationTableRowStatus,
)

internal data class ConversationTableArtifactSnapshot(
    val title: String,
    val sourceMessageCount: Int,
    val rows: List<ConversationTableRow>,
)

internal data class ConversationTableArtifact(
    val id: Long,
    val sessionId: String,
    val title: String,
    val sourceMessageCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val rows: List<ConversationTableRow>,
)

internal data class ConversationTableArtifactSummary(
    val id: Long,
    val sessionId: String,
    val title: String,
    val rowCount: Int,
    val updatedAt: Long,
)
