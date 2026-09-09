package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.base.StockChatLog
import com.guet.liang.stockchat.base.toUserMessage
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.ConversationMindMapArtifact
import com.guet.liang.stockchat.model.ConversationStockComparisonSnapshot
import com.guet.liang.stockchat.model.MermaidMindMapNode
import com.guet.liang.stockchat.model.StockQuote

/** Coordinates artifact persistence, generation and refresh without depending on a Kuikly page. */
internal class ArtifactController(
    private val sessions: ChatSessionRepository,
    private val tables: TableArtifactStore,
    private val mindMaps: MindMapArtifactStore,
    private val generator: ArtifactGenerator,
    private val market: ArtifactMarketRepository,
    private val loadFavorites: () -> List<StockQuote>,
    private val onComparisonChanged: (ComparisonDetailUiState) -> Unit = {},
) {
    private var refreshToken = 0
    private var baseSnapshot: ConversationStockComparisonSnapshot? = null

    fun tableArtifacts() = operation("本地表格对比暂时无法读取，请稍后重试。") { tables.listAll() }

    fun mindMapArtifacts() = operation("本地思维导图暂时无法读取，请稍后重试。") { mindMaps.listAll() }

    fun loadMindMap(idText: String): ArtifactResult<ConversationMindMapArtifact?> {
        val id = idText.toLongOrNull()
        if (id == null || id <= 0L) return ArtifactResult.Failure("产物标识无效，请返回列表重新选择。")
        return operation("本地思维导图暂时无法读取，请稍后重试。") { mindMaps.load(id) }
    }

    fun createTable(sessionId: String, title: String, messages: List<ChatMessage>): ArtifactResult<Long> {
        if (messages.none { it.role == ChatRole.USER }) return ArtifactResult.Failure("当前对话还没有可对比的内容")
        return operation("会话表格对比生成失败，请重试") {
            val comparison = generator.comparison(title, messages)
            if (comparison.rows.isEmpty()) {
                return@operation ArtifactResult.Failure("当前会话未识别到股票或指数")
            }
            sessions.replaceMessages(sessionId, messages)
            ArtifactResult.Success(tables.upsert(sessionId, generator.tableSnapshot(comparison)))
        }.flatten()
    }

    fun createMindMap(sessionId: String, title: String, messages: List<ChatMessage>): ArtifactResult<Long> {
        if (messages.none { it.role == ChatRole.USER }) return ArtifactResult.Failure("当前对话还没有可梳理的内容")
        return operation("思维导图生成失败，请重试") {
            sessions.replaceMessages(sessionId, messages)
            mindMaps.upsert(sessionId, generator.mindMap(title, messages))
        }
    }

    fun loadComparison(idText: String) {
        cancelRefresh()
        baseSnapshot = null
        val id = idText.toLongOrNull()
        if (id == null || id <= 0L) {
            onComparisonChanged(ComparisonDetailUiState.Error("对比标识无效，请返回列表重新选择。"))
            return
        }
        onComparisonChanged(ComparisonDetailUiState.Loading)
        when (val result = operation("当前会话暂时无法整理，请稍后重试。") {
            tables.load(id)?.let { generator.comparison(it.title, sessions.loadMessages(it.sessionId)) }
        }) {
            is ArtifactResult.Failure -> onComparisonChanged(ComparisonDetailUiState.Error(result.message))
            is ArtifactResult.Success -> applyLoadedComparison(result.value)
        }
    }

    fun refreshComparison() {
        val snapshot = baseSnapshot ?: return
        val count = snapshot.providerSymbols.size
        if (count == 0) {
            onComparisonChanged(ComparisonDetailUiState.Content(comparisonContent(snapshot, ComparisonRefreshPhase.SESSION_ONLY)))
            return
        }
        val token = ++refreshToken
        onComparisonChanged(ComparisonDetailUiState.Refreshing(comparisonContent(snapshot, ComparisonRefreshPhase.REFRESHING)))
        market.refresh(snapshot) { result ->
            if (token == refreshToken) {
                val phase = when {
                    result.requestedCount == 0 -> ComparisonRefreshPhase.SESSION_ONLY
                    result.isComplete -> ComparisonRefreshPhase.CURRENT
                    result.isPartial -> ComparisonRefreshPhase.PARTIAL
                    else -> ComparisonRefreshPhase.FAILED
                }
                onComparisonChanged(ComparisonDetailUiState.Content(ComparisonContentUi(
                    snapshot = result.snapshot,
                    refreshPhase = phase,
                    refreshedCount = result.refreshedCount,
                    completedCount = result.requestedCount,
                    refreshTargetCount = result.requestedCount,
                )))
            }
        }
    }

    fun cancelRefresh() { refreshToken += 1 }

    fun favorites(): List<StockQuote> = loadFavorites()

    fun providerSymbol(quote: StockQuote): String = generator.providerSymbol(quote) ?: quote.symbol

    fun mindMapTree(artifact: ConversationMindMapArtifact): MermaidMindMapNode = generator.parseMindMap(artifact.mermaidSource)
        ?.takeIf { it.children.isNotEmpty() || artifact.branches.isEmpty() }
        ?: fallbackArtifactMindMap(artifact)

    private fun applyLoadedComparison(snapshot: ConversationStockComparisonSnapshot?) {
        baseSnapshot = snapshot
        when {
            snapshot == null -> onComparisonChanged(ComparisonDetailUiState.NotFound)
            snapshot.rows.isEmpty() -> onComparisonChanged(ComparisonDetailUiState.Empty(snapshot.title, snapshot.sourceMessageCount))
            else -> refreshComparison()
        }
    }

    private fun comparisonContent(snapshot: ConversationStockComparisonSnapshot, phase: ComparisonRefreshPhase) = ComparisonContentUi(
        snapshot = snapshot,
        refreshPhase = phase,
        refreshedCount = 0,
        completedCount = 0,
        refreshTargetCount = snapshot.providerSymbols.size,
    )
}

private fun <T> operation(fallback: String, action: () -> T): ArtifactResult<T> = try {
    ArtifactResult.Success(action())
} catch (exception: RuntimeException) {
    StockChatLog.w("ArtifactController", "artifact operation failed", exception)
    ArtifactResult.Failure(exception.toUserMessage(fallback))
}

private fun <T> ArtifactResult<ArtifactResult<T>>.flatten(): ArtifactResult<T> = when (this) {
    is ArtifactResult.Success -> value
    is ArtifactResult.Failure -> this
}
