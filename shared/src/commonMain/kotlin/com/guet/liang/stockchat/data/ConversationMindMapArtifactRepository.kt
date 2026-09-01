package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.database.StockChatDatabase
import com.guet.liang.stockchat.model.ConversationMindMapArtifact
import com.guet.liang.stockchat.model.ConversationMindMapArtifactSnapshot
import com.guet.liang.stockchat.model.ConversationMindMapArtifactSummary
import com.guet.liang.stockchat.model.ConversationMindMapBranch
import com.guet.liang.stockchat.model.ConversationTableRowStatus

internal class ConversationMindMapArtifactRepository(
    private val database: StockChatDatabase,
) {
    private val queries = database.chatHistoryQueries

    fun upsert(
        sessionId: String,
        snapshot: ConversationMindMapArtifactSnapshot,
    ): Long {
        require(sessionId.isNotBlank()) { "Conversation mind map artifact session id must not be blank." }
        require(queries.selectSession(sessionId).executeAsOneOrNull() != null) {
            "Cannot save a conversation mind map artifact before its chat session exists."
        }
        require(snapshot.sourceMessageCount >= 0) {
            "Conversation mind map artifact source message count must not be negative."
        }

        val normalizedTitle = snapshot.title.normalizedTitle()
        var artifactId = 0L
        database.transaction {
            queries.insertConversationMindMapArtifact(
                session_id = sessionId,
                title = normalizedTitle,
                source_message_count = snapshot.sourceMessageCount.toLong(),
                branch_count = snapshot.branches.size.toLong(),
            )
            queries.updateConversationMindMapArtifact(
                title = normalizedTitle,
                source_message_count = snapshot.sourceMessageCount.toLong(),
                branch_count = snapshot.branches.size.toLong(),
                session_id = sessionId,
            )
            artifactId = queries.selectConversationMindMapArtifactIdBySession(sessionId).executeAsOne()
            queries.deleteConversationMindMapArtifactBranches(artifactId)
            snapshot.branches.forEachIndexed { branchIndex, branch ->
                queries.insertConversationMindMapArtifactBranch(
                    artifact_id = artifactId,
                    branch_index = branchIndex.toLong(),
                    sequence_number = branch.sequence.toLong(),
                    topic = branch.topic,
                    insight = branch.insight,
                    related_instrument = branch.relatedInstrument,
                    status = branch.status.name,
                )
            }
        }
        check(artifactId > 0L) { "Conversation mind map artifact id was not created." }
        return artifactId
    }

    fun load(artifactId: Long): ConversationMindMapArtifact? {
        val storedArtifact = queries
            .selectConversationMindMapArtifact(artifactId)
            .executeAsOneOrNull()
            ?: return null
        return ConversationMindMapArtifact(
            id = storedArtifact.id,
            sessionId = storedArtifact.session_id,
            title = storedArtifact.title,
            sourceMessageCount = storedArtifact.source_message_count.toInt(),
            createdAt = storedArtifact.created_at,
            updatedAt = storedArtifact.updated_at,
            branches = loadBranches(storedArtifact.id),
        )
    }

    fun listAll(): List<ConversationMindMapArtifactSummary> {
        return queries.selectConversationMindMapArtifacts().executeAsList().map { storedArtifact ->
            ConversationMindMapArtifactSummary(
                id = storedArtifact.id,
                sessionId = storedArtifact.session_id,
                title = storedArtifact.title,
                branchCount = storedArtifact.branch_count.toInt(),
                updatedAt = storedArtifact.updated_at,
            )
        }
    }

    private fun loadBranches(artifactId: Long): List<ConversationMindMapBranch> {
        return queries.selectConversationMindMapArtifactBranches(artifactId).executeAsList().map { storedBranch ->
            ConversationMindMapBranch(
                sequence = storedBranch.sequence_number.toInt(),
                topic = storedBranch.topic,
                insight = storedBranch.insight,
                relatedInstrument = storedBranch.related_instrument,
                status = enumValues<ConversationTableRowStatus>()
                    .firstOrNull { status -> status.name == storedBranch.status }
                    ?: ConversationTableRowStatus.WAITING,
            )
        }
    }

    private fun String.normalizedTitle(): String {
        return trim().take(MAX_ARTIFACT_TITLE_LENGTH).ifBlank { DEFAULT_ARTIFACT_TITLE }
    }

    private companion object {
        const val MAX_ARTIFACT_TITLE_LENGTH = 80
        const val DEFAULT_ARTIFACT_TITLE = "当前会话 · 思维导图"
    }
}
