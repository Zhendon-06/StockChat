package com.guet.liang.stockchat.model

internal enum class ConversationTableColumn(
    val key: String,
    val title: String,
) {
    SEQUENCE("sequence", "序号"),
    USER_QUESTION("user_question", "用户问题"),
    AI_ANSWER_SUMMARY("ai_answer_summary", "AI 回答摘要"),
    RELATED_INSTRUMENT("related_instrument", "相关标的"),
    STATUS("status", "状态"),
}

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
) {
    fun valueFor(column: ConversationTableColumn): String {
        return when (column) {
            ConversationTableColumn.SEQUENCE -> sequence.toString()
            ConversationTableColumn.USER_QUESTION -> userQuestion
            ConversationTableColumn.AI_ANSWER_SUMMARY -> aiAnswerSummary
            ConversationTableColumn.RELATED_INSTRUMENT -> relatedInstrument
            ConversationTableColumn.STATUS -> status.label
        }
    }
}

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
