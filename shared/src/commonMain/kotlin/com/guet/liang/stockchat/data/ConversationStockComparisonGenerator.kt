package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.ConversationStockComparisonRow
import com.guet.liang.stockchat.model.ConversationStockComparisonSnapshot
import com.guet.liang.stockchat.model.ConversationStockDataSource
import com.guet.liang.stockchat.model.ConversationTableArtifactSnapshot
import com.guet.liang.stockchat.model.ConversationTableRow
import com.guet.liang.stockchat.model.ConversationTableRowStatus
import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TencentMarketSnapshot

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal object ConversationStockComparisonGenerator {
    fun generate(
        title: String,
        messages: List<ChatMessage>,
    ): ConversationStockComparisonSnapshot {
        val rowsByIdentity = linkedMapOf<String, ConversationStockComparisonRow>()
        messages.forEach { message ->
            message.blocks.forEach { block -> mergeBlock(rowsByIdentity, message, block) }
        }
        return ConversationStockComparisonSnapshot(
            title = normalizedTitle(title),
            sourceMessageCount = messages.size,
            rows = rowsByIdentity.values.toList(),
        )
    }

    fun overlay(
        snapshot: ConversationStockComparisonSnapshot,
        marketSnapshots: List<TencentMarketSnapshot>,
    ): ConversationStockComparisonSnapshot {
        if (marketSnapshots.isEmpty() || snapshot.rows.isEmpty()) {
            return snapshot
        }
        val snapshotsByProvider = marketSnapshots.associateBy {
            it.providerSymbol.trim().lowercase()
        }
        return snapshot.copy(
            rows = snapshot.rows.map { row ->
                val marketSnapshot = snapshotsByProvider[row.providerSymbol.lowercase()]
                    ?: marketSnapshots.singleOrNull { candidate ->
                        candidate.quote.symbol.equals(row.symbol, ignoreCase = true)
                    }
                marketSnapshot?.let { row.withMarketSnapshot(it) } ?: row
            },
        )
    }

    fun toArtifactSnapshot(
        snapshot: ConversationStockComparisonSnapshot,
    ): ConversationTableArtifactSnapshot {
        return ConversationTableArtifactSnapshot(
            title = snapshot.title,
            sourceMessageCount = snapshot.sourceMessageCount,
            rows = snapshot.rows.mapIndexed { index, row ->
                ConversationTableRow(
                    sequence = index + 1,
                    userQuestion = row.sourceDescription,
                    aiAnswerSummary = row.marketMetricSummary(),
                    relatedInstrument = "${row.displayName}（${row.symbol}）",
                    status = if (row.hasQuote) {
                        ConversationTableRowStatus.COMPLETED
                    } else {
                        ConversationTableRowStatus.WAITING
                    },
                )
            },
        )
    }

    private fun mergeBlock(
        rowsByIdentity: MutableMap<String, ConversationStockComparisonRow>,
        message: ChatMessage,
        block: AnswerBlock,
    ) {
        when (block) {
            is AnswerBlock.Markdown -> detectSecurities(block.source.ifBlank { block.fallbackText }).forEach {
                mergeMention(rowsByIdentity, it, message)
            }
            is AnswerBlock.MarketQuote -> mergeQuote(rowsByIdentity, block.quote, message)
            is AnswerBlock.ImageGallery -> Unit
        }
    }

    private fun mergeMention(
        rowsByIdentity: MutableMap<String, ConversationStockComparisonRow>,
        identity: SecurityIdentity,
        message: ChatMessage,
    ) {
        val key = identity.key
        val existing = rowsByIdentity[key] ?: identity.toComparisonRow()
        rowsByIdentity[key] = existing.copy(
            providerSymbol = existing.providerSymbol.ifBlank { identity.providerSymbol },
            name = existing.name.ifBlank { identity.name },
            symbol = existing.symbol.ifBlank { identity.symbol },
            marketLabel = existing.marketLabel.ifBlank { identity.marketLabel },
            mentionedByUser = existing.mentionedByUser || message.role == ChatRole.USER,
            generatedByAi = existing.generatedByAi || message.role == ChatRole.ASSISTANT,
            relatedMessageIds = (existing.relatedMessageIds + message.id).distinct(),
        )
    }

    private fun mergeQuote(
        rowsByIdentity: MutableMap<String, ConversationStockComparisonRow>,
        quote: StockQuote,
        message: ChatMessage,
    ) {
        val identity = identityForQuote(quote)
        val key = identity.key
        val existing = rowsByIdentity[key] ?: identity.toComparisonRow()
        val summaryMetrics = parseSummaryMetrics(quote.summary)
        rowsByIdentity[key] = existing.copy(
            providerSymbol = identity.providerSymbol.ifBlank { existing.providerSymbol },
            name = quote.name.ifBlank { existing.name },
            symbol = quote.symbol.normalizedDisplaySymbol().ifBlank { existing.symbol },
            marketLabel = quote.marketLabel.ifBlank { existing.marketLabel },
            price = quote.price,
            change = quote.change,
            changePercent = quote.changePercent,
            previousClose = summaryMetrics.previousClose,
            open = summaryMetrics.open,
            high = summaryMetrics.high,
            low = summaryMetrics.low,
            volume = summaryMetrics.volume,
            volumeUnit = summaryMetrics.volumeUnit,
            amount = summaryMetrics.amount,
            amountUnit = summaryMetrics.amountUnit,
            turnoverRate = summaryMetrics.turnoverRate,
            priceEarningsRatio = summaryMetrics.priceEarningsRatio,
            amplitude = summaryMetrics.amplitude,
            updatedAt = quote.updatedAt,
            trendPoints = quote.trendPoints,
            summary = quote.summary,
            aiInsight = quote.aiInsight,
            mentionedByUser = existing.mentionedByUser || message.role == ChatRole.USER,
            generatedByAi = existing.generatedByAi || message.role == ChatRole.ASSISTANT,
            dataSource = ConversationStockDataSource.CONVERSATION_QUOTE,
            relatedMessageIds = (existing.relatedMessageIds + message.id).distinct(),
        )
    }

    private fun ConversationStockComparisonRow.withMarketSnapshot(
        snapshot: TencentMarketSnapshot,
    ): ConversationStockComparisonRow {
        val quote = snapshot.quote
        return copy(
            providerSymbol = snapshot.providerSymbol.lowercase(),
            name = quote.name.ifBlank { name },
            symbol = quote.symbol.ifBlank { symbol },
            marketLabel = quote.marketLabel.ifBlank { marketLabel },
            price = quote.price,
            change = quote.change,
            changePercent = quote.changePercent,
            previousClose = snapshot.previousClose,
            open = snapshot.open,
            high = snapshot.high,
            low = snapshot.low,
            volume = snapshot.volume,
            volumeUnit = snapshot.volumeUnit,
            amount = snapshot.amount,
            amountUnit = snapshot.amountUnit,
            turnoverRate = snapshot.turnoverRate,
            priceEarningsRatio = snapshot.priceEarningsRatio,
            amplitude = snapshot.amplitude,
            updatedAt = quote.updatedAt,
            trendPoints = quote.trendPoints,
            summary = quote.summary,
            aiInsight = quote.aiInsight,
            dataSource = ConversationStockDataSource.FRESH_MARKET,
        )
    }

    private fun normalizedTitle(title: String): String {
        val baseTitle = title.trim()
            .replace(WHITESPACE_REGEX, " ")
            .removeSuffix(TABLE_ARTIFACT_TITLE_SUFFIX)
            .removeSuffix(COMPARISON_TITLE_SUFFIX)
            .trim()
            .take(MAX_TITLE_LENGTH)
            .ifBlank { DEFAULT_TITLE }
        return "$baseTitle$COMPARISON_TITLE_SUFFIX"
    }

    private const val MAX_TITLE_LENGTH = 60
    private const val DEFAULT_TITLE = "当前会话"
    private const val TABLE_ARTIFACT_TITLE_SUFFIX = " · 产物表格"
    private const val COMPARISON_TITLE_SUFFIX = " · 表格对比"
    private val WHITESPACE_REGEX = Regex("\\s+")
}
