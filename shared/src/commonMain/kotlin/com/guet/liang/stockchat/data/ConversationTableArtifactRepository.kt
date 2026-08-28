package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.database.StockChatDatabase
import com.guet.liang.stockchat.model.ConversationTableArtifact
import com.guet.liang.stockchat.model.ConversationTableArtifactSnapshot
import com.guet.liang.stockchat.model.ConversationTableArtifactSummary
import com.guet.liang.stockchat.model.ConversationTableRow
import com.guet.liang.stockchat.model.ConversationTableRowStatus

internal class ConversationTableArtifactRepository(
    private val database: StockChatDatabase,
) {
    private val queries = database.chatHistoryQueries

    fun upsert(
        sessionId: String,
        snapshot: ConversationTableArtifactSnapshot,
    ): Long {
        require(sessionId.isNotBlank()) { "Conversation table artifact session id must not be blank." }
        require(queries.selectSession(sessionId).executeAsOneOrNull() != null) {
            "Cannot save a conversation table artifact before its chat session exists."
        }
        require(snapshot.sourceMessageCount >= 0) {
            "Conversation table artifact source message count must not be negative."
        }

        val normalizedTitle = snapshot.title.normalizedTitle()
        var artifactId = 0L
        database.transaction {
            queries.insertConversationTableArtifact(
                session_id = sessionId,
                title = normalizedTitle,
                source_message_count = snapshot.sourceMessageCount.toLong(),
                row_count = snapshot.rows.size.toLong(),
            )
            queries.updateConversationTableArtifact(
                title = normalizedTitle,
                source_message_count = snapshot.sourceMessageCount.toLong(),
                row_count = snapshot.rows.size.toLong(),
                session_id = sessionId,
            )
            artifactId = queries.selectConversationTableArtifactIdBySession(sessionId).executeAsOne()
            queries.deleteConversationTableArtifactRows(artifactId)
            snapshot.rows.forEachIndexed { rowIndex, row ->
                queries.insertConversationTableArtifactRow(
                    artifact_id = artifactId,
                    row_index = rowIndex.toLong(),
                    sequence_number = row.sequence.toLong(),
                    user_question = row.userQuestion,
                    ai_answer_summary = row.aiAnswerSummary,
                    related_instrument = row.relatedInstrument,
                    status = row.status.name,
                )
            }
        }
        check(artifactId > 0L) { "Conversation table artifact id was not created." }
        return artifactId
    }

    fun load(artifactId: Long): ConversationTableArtifact? {
        val storedArtifact = queries
            .selectConversationTableArtifact(artifactId)
            .executeAsOneOrNull()
            ?: return null
        return ConversationTableArtifact(
            id = storedArtifact.id,
            sessionId = storedArtifact.session_id,
            title = storedArtifact.title,
            sourceMessageCount = storedArtifact.source_message_count.toInt(),
            createdAt = storedArtifact.created_at,
            updatedAt = storedArtifact.updated_at,
            rows = loadRows(storedArtifact.id),
        )
    }

    fun listAll(): List<ConversationTableArtifactSummary> {
        return queries.selectConversationTableArtifacts().executeAsList().map { storedArtifact ->
            ConversationTableArtifactSummary(
                id = storedArtifact.id,
                sessionId = storedArtifact.session_id,
                title = storedArtifact.title,
                rowCount = storedArtifact.row_count.toInt(),
                updatedAt = storedArtifact.updated_at,
            )
        }
    }

    private fun loadRows(artifactId: Long): List<ConversationTableRow> {
        return queries.selectConversationTableArtifactRows(artifactId).executeAsList().map { storedRow ->
            ConversationTableRow(
                sequence = storedRow.sequence_number.toInt(),
                userQuestion = storedRow.user_question,
                aiAnswerSummary = storedRow.ai_answer_summary,
                relatedInstrument = storedRow.related_instrument,
                status = enumValues<ConversationTableRowStatus>()
                    .firstOrNull { status -> status.name == storedRow.status }
                    ?: ConversationTableRowStatus.WAITING,
            )
        }
    }

    private fun String.normalizedTitle(): String {
        return trim().take(MAX_ARTIFACT_TITLE_LENGTH).ifBlank { DEFAULT_ARTIFACT_TITLE }
    }

    private companion object {
        const val MAX_ARTIFACT_TITLE_LENGTH = 80
        const val DEFAULT_ARTIFACT_TITLE = "当前会话 · 产物表格"
    }
}
