package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.database.StockChatDatabase
import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.MessageState
import com.guet.liang.stockchat.model.StockQuote

internal data class ChatSessionSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val isArchived: Boolean = false,
)

internal class ChatHistoryRepository(
    private val database: StockChatDatabase,
) {
    private val queries = database.chatHistoryQueries

    fun loadSessions(): List<ChatSessionSummary> {
        return queries.selectSessions().executeAsList().map {
            ChatSessionSummary(
                id = it.id,
                title = it.title,
                updatedAt = it.updated_at,
                isArchived = it.is_archived != 0L,
            )
        }
    }

    fun loadArchivedSessions(): List<ChatSessionSummary> {
        return queries.selectArchivedSessions().executeAsList().map {
            ChatSessionSummary(
                id = it.id,
                title = it.title,
                updatedAt = it.updated_at,
                isArchived = it.is_archived != 0L,
            )
        }
    }

    fun loadMessages(sessionId: String): List<ChatMessage> {
        return queries.selectMessages(sessionId).executeAsList().mapNotNull { storedMessage ->
            val role = enumValueOrNull<ChatRole>(storedMessage.role) ?: return@mapNotNull null
            val state = enumValueOrNull<MessageState>(storedMessage.state) ?: MessageState.DELIVERED
            ChatMessage(
                id = storedMessage.id,
                role = role,
                blocks = loadBlocks(storedMessage.id),
                state = state,
                retryQuestion = storedMessage.retry_question,
                retryAttempt = storedMessage.retry_attempt.toInt(),
                errorMessage = storedMessage.error_message,
            )
        }
    }

    fun replaceMessages(sessionId: String, messages: List<ChatMessage>) {
        database.transaction {
            queries.insertSession(sessionId, sessionTitle(messages))
            queries.touchSession(sessionId)
            deleteSessionContent(sessionId)
            messages.forEachIndexed { messageIndex, message ->
                queries.insertMessage(
                    id = message.id,
                    session_id = sessionId,
                    role = message.role.name,
                    state = message.state.name,
                    retry_question = message.retryQuestion,
                    retry_attempt = message.retryAttempt.toLong(),
                    error_message = message.errorMessage,
                    sort_order = messageIndex.toLong(),
                )
                message.blocks.forEachIndexed { blockIndex, block ->
                    insertBlock(message.id, blockIndex, block)
                }
            }
        }
    }

    fun clearSession(sessionId: String) {
        database.transaction {
            deleteSessionContent(sessionId)
            queries.deleteSession(sessionId)
        }
    }

    fun renameSession(sessionId: String, title: String) {
        queries.renameSession(title, sessionId)
    }

    fun archiveSession(sessionId: String): Boolean {
        if (sessionId.isBlank()) {
            return false
        }
        return queries.archiveSession(sessionId).value > 0L
    }

    fun restoreSession(sessionId: String): Boolean {
        if (sessionId.isBlank()) {
            return false
        }
        return queries.restoreSession(sessionId).value > 0L
    }

    private fun loadBlocks(messageId: String): List<AnswerBlock> {
        return queries.selectBlocks(messageId).executeAsList().mapNotNull { block ->
            when (block.block_type) {
                BLOCK_MARKDOWN -> AnswerBlock.Markdown(
                    source = block.markdown_source.orEmpty(),
                    fallbackText = block.fallback_text.orEmpty(),
                )
                BLOCK_MARKET_QUOTE -> queries.selectMarketQuote(block.id).executeAsOneOrNull()?.let { quote ->
                    AnswerBlock.MarketQuote(
                        StockQuote(
                            name = quote.name,
                            symbol = quote.symbol,
                            marketLabel = quote.market_label,
                            price = quote.price,
                            change = quote.change_value,
                            changePercent = quote.change_percent,
                            updatedAt = quote.updated_at,
                            isPositive = quote.is_positive != 0L,
                            trendPoints = queries.selectTrendPoints(block.id).executeAsList().map(Double::toFloat),
                            summary = quote.summary,
                            aiInsight = quote.ai_insight,
                        )
                    )
                }
                BLOCK_IMAGE_GALLERY -> storedImageList(block.markdown_source)
                    .takeIf { it.isNotEmpty() }
                    ?.let { images -> AnswerBlock.ImageGallery(images = images) }
                else -> null
            }
        }
    }

    private fun insertBlock(messageId: String, blockIndex: Int, block: AnswerBlock) {
        when (block) {
            is AnswerBlock.Markdown -> {
                queries.insertBlock(
                    message_id = messageId,
                    block_index = blockIndex.toLong(),
                    block_type = BLOCK_MARKDOWN,
                    markdown_source = block.source,
                    fallback_text = block.fallbackText,
                )
            }
            is AnswerBlock.MarketQuote -> {
                queries.insertBlock(
                    message_id = messageId,
                    block_index = blockIndex.toLong(),
                    block_type = BLOCK_MARKET_QUOTE,
                    markdown_source = null,
                    fallback_text = null,
                )
                val blockId = queries.lastInsertedBlockId().executeAsOne()
                val quote = block.quote
                queries.insertMarketQuote(
                    block_id = blockId,
                    name = quote.name,
                    symbol = quote.symbol,
                    market_label = quote.marketLabel,
                    price = quote.price,
                    change_value = quote.change,
                    change_percent = quote.changePercent,
                    updated_at = quote.updatedAt,
                    is_positive = if (quote.isPositive) 1L else 0L,
                    summary = quote.summary,
                    ai_insight = quote.aiInsight,
                )
                quote.trendPoints.forEachIndexed { pointIndex, point ->
                    queries.insertTrendPoint(blockId, pointIndex.toLong(), point.toDouble())
                }
            }
            is AnswerBlock.ImageGallery -> {
                queries.insertBlock(
                    message_id = messageId,
                    block_index = blockIndex.toLong(),
                    block_type = BLOCK_IMAGE_GALLERY,
                    markdown_source = block.images.joinToString("\n"),
                    fallback_text = null,
                )
            }
        }
    }

    private fun storedImageList(value: String?): List<String> {
        return value
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
    }

    private fun deleteSessionContent(sessionId: String) {
        queries.deleteTrendPointsForSession(sessionId)
        queries.deleteMarketQuotesForSession(sessionId)
        queries.deleteBlocksForSession(sessionId)
        queries.deleteMessagesForSession(sessionId)
    }

    private fun sessionTitle(messages: List<ChatMessage>): String {
        return messages.firstOrNull { it.role == ChatRole.USER }
            ?.blocks
            ?.filterIsInstance<AnswerBlock.Markdown>()
            ?.firstOrNull()
            ?.source
            ?.trim()
            ?.take(40)
            .orEmpty()
            .ifBlank { DEFAULT_SESSION_TITLE }
    }

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? {
        return enumValues<T>().firstOrNull { it.name == value }
    }

    private companion object {
        const val DEFAULT_SESSION_TITLE = "新对话"
        const val BLOCK_MARKDOWN = "markdown"
        const val BLOCK_MARKET_QUOTE = "market_quote"
        const val BLOCK_IMAGE_GALLERY = "image_gallery"
    }
}

internal object ChatHistoryDatabase {
    private var repository: ChatHistoryRepository? = null
    private var artifactRepository: ConversationTableArtifactRepository? = null
    private var mindMapArtifactRepository: ConversationMindMapArtifactRepository? = null

    fun initialize(database: StockChatDatabase) {
        repository = ChatHistoryRepository(database)
        artifactRepository = ConversationTableArtifactRepository(database)
        mindMapArtifactRepository = ConversationMindMapArtifactRepository(database)
    }

    fun repository(): ChatHistoryRepository {
        return checkNotNull(repository) { "SQLDelight database has not been initialized." }
    }

    fun artifactRepository(): ConversationTableArtifactRepository {
        return checkNotNull(artifactRepository) { "SQLDelight database has not been initialized." }
    }

    fun mindMapArtifactRepository(): ConversationMindMapArtifactRepository {
        return checkNotNull(mindMapArtifactRepository) { "SQLDelight database has not been initialized." }
    }
}
