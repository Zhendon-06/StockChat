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

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class ChartConclusion(
    val text: String,
    val reference: ChartEvidenceReference? = null,
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal sealed class ChartEvidenceResolution {
    data class Valid(val indices: IntRange, val label: String) : ChartEvidenceResolution()
    data class Invalid(val reason: String) : ChartEvidenceResolution()
}
