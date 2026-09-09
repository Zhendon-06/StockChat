package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.controller.ArtifactGenerator
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ConversationStockComparisonSnapshot
import com.guet.liang.stockchat.model.StockQuote

/** Adapts the existing pure artifact generators to controller contracts. */
internal object LocalArtifactGenerator : ArtifactGenerator {
    override fun comparison(title: String, messages: List<ChatMessage>) =
        ConversationStockComparisonGenerator.generate(title, messages)

    override fun tableSnapshot(comparison: ConversationStockComparisonSnapshot) =
        ConversationStockComparisonGenerator.toArtifactSnapshot(comparison)

    override fun mindMap(title: String, messages: List<ChatMessage>) =
        ConversationMindMapArtifactGenerator.generate(title, messages)

    override fun parseMindMap(source: String) = MermaidMindMapParser.parse(source)

    override fun providerSymbol(quote: StockQuote) = providerSymbolForQuote(quote)
}
