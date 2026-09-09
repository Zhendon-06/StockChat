package com.guet.liang.stockchat.controller

import com.guet.liang.kuiklychart.finance.FinancialPoint
import com.guet.liang.stockchat.data.ChartEvidenceResolver
import com.guet.liang.stockchat.model.ChartEvidenceReference
import com.guet.liang.stockchat.model.ChartEvidenceResolution
import com.guet.liang.stockchat.model.MarketPeriod

/** Uses the same strict evidence validation as the data pipeline for rendered detail charts. */
internal fun resolveDetailEvidence(
    reference: ChartEvidenceReference?,
    symbol: String,
    period: MarketPeriod,
    sourceUpdatedAt: String,
    points: List<FinancialPoint>,
): ChartEvidenceResolution = ChartEvidenceResolver.resolve(reference, symbol, period, sourceUpdatedAt, points)
