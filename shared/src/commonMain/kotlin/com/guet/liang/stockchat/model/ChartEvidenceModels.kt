package com.guet.liang.stockchat.model

/** Explicit evidence supplied alongside a conclusion, never extracted from its prose. */
internal data class ChartEvidenceReference(
    val symbol: String,
    val period: String,
    val startDate: String,
    val endDate: String,
    val metric: String,
    val sourceUpdatedAt: String,
)

internal data class ChartConclusion(
    val text: String,
    val reference: ChartEvidenceReference? = null,
)
