package com.guet.liang.stockchat.model

/** Immutable exposure summary rendered by the risk map page. */
internal data class RiskMapSnapshot(
    val total: Int,
    val rising: Int,
    val falling: Int,
    val unchanged: Int,
    val averageChange: Float,
    val largestMove: StockQuote?,
    val concentrationLabel: String,
    val headline: String,
)
