package com.guet.liang.kuiklychart.finance

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
public data class FinancialEvidenceViewport(val start: Int, val count: Int)

/** Include the complete evidence plus context, even when it was outside the old viewport. */
public fun financialEvidenceViewport(size: Int, range: IntRange): FinancialEvidenceViewport? {
    if (size <= 0 || range.isEmpty()) return null
    if (range.first < 0 || range.last >= size) return null
    val length = range.last - range.first + 1
    val padding = maxOf(2, length / 5)
    val count = (length + padding * 2).coerceAtLeast(10).coerceAtMost(size)
    val start = (range.first - (count - length) / 2).coerceIn(0, size - count)
    return FinancialEvidenceViewport(start, count)
}
