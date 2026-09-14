package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TodayMarketSectorObservation
import com.tencent.kuikly.core.base.Color

/** User actions and rotating-card state shared by the market bento sections. */
internal data class TodayMarketInteractions(
    val onQuoteClick: (StockQuote) -> Unit,
    val onSectorClick: (TodayMarketSectorObservation) -> Unit,
    val indexFocus: () -> Int,
    val indexPulse: () -> Boolean,
    val indexRevealPhase: () -> Int,
    val onAdvanceIndexFocus: () -> Unit,
    val sectorFocus: () -> Int,
    val sectorPulse: () -> Boolean,
    val sectorRevealPhase: () -> Int,
    val onAdvanceSectorFocus: () -> Unit,
    val quoteFocus: () -> Int,
    val quotePulse: () -> Boolean,
    val quoteRevealPhase: () -> Int,
    val onAdvanceQuoteFocus: () -> Unit,
)

internal fun marketContentWidth(pageWidth: Float, scale: Float): Float =
    (pageWidth - 36f * scale).coerceAtLeast(1f)

internal fun marketQuoteColor(quote: StockQuote): Color =
    if (quote.isPositive) StockChatTheme.positive else StockChatTheme.negative

internal fun marketSectorColor(sector: TodayMarketSectorObservation): Color =
    if (sector.isPositive) StockChatTheme.positive else StockChatTheme.negative
