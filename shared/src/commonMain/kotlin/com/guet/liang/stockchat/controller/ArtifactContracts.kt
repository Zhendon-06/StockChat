package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ConversationMindMapArtifact
import com.guet.liang.stockchat.model.ConversationMindMapArtifactSnapshot
import com.guet.liang.stockchat.model.ConversationMindMapArtifactSummary
import com.guet.liang.stockchat.model.ConversationStockComparisonRefreshResult
import com.guet.liang.stockchat.model.ConversationStockComparisonSnapshot
import com.guet.liang.stockchat.model.ConversationTableArtifact
import com.guet.liang.stockchat.model.ConversationTableArtifactSnapshot
import com.guet.liang.stockchat.model.ConversationTableArtifactSummary
import com.guet.liang.stockchat.model.MermaidMindMapNode
import com.guet.liang.stockchat.model.StockQuote

/** Table persistence independent of the database driver and page lifecycle. */
internal interface TableArtifactStore {
    fun upsert(sessionId: String, snapshot: ConversationTableArtifactSnapshot): Long
    fun load(artifactId: Long): ConversationTableArtifact?
    fun listAll(): List<ConversationTableArtifactSummary>
}

/** Mind map persistence independent of the database driver and page lifecycle. */
internal interface MindMapArtifactStore {
    fun upsert(sessionId: String, snapshot: ConversationMindMapArtifactSnapshot): Long
    fun load(artifactId: Long): ConversationMindMapArtifact?
    fun listAll(): List<ConversationMindMapArtifactSummary>
}

/** Pure transformations used for creating and displaying conversation artifacts. */
internal interface ArtifactGenerator {
    fun comparison(title: String, messages: List<ChatMessage>): ConversationStockComparisonSnapshot
    fun tableSnapshot(comparison: ConversationStockComparisonSnapshot): ConversationTableArtifactSnapshot
    fun mindMap(title: String, messages: List<ChatMessage>): ConversationMindMapArtifactSnapshot
    fun parseMindMap(source: String): MermaidMindMapNode?
    fun providerSymbol(quote: StockQuote): String?
}

/** Refresh boundary supports deferred and failing market responses in controller tests. */
internal fun interface ArtifactMarketRepository {
    fun refresh(snapshot: ConversationStockComparisonSnapshot, callback: (ConversationStockComparisonRefreshResult) -> Unit)
}

/** Operation results contain user-facing feedback without requiring a bridge or Pager. */
internal sealed class ArtifactResult<out T> {
    data class Success<T>(val value: T) : ArtifactResult<T>()
    data class Failure(val message: String) : ArtifactResult<Nothing>()
}
