package com.guet.liang.kuiklychart.finance

import kotlin.test.*

class FinancialEvidenceViewportTest {
    @Test fun revealsOldRecentSingleAndEntireHistoryWithContext() {
        listOf(0..4, 100..104, 235..239, 120..120, 0..239).forEach { range ->
            val result = assertNotNull(financialEvidenceViewport(240, range))
            assertTrue(result.start >= 0)
            assertTrue(result.start + result.count <= 240)
            assertTrue(range.first >= result.start)
            assertTrue(range.last < result.start + result.count)
            assertTrue(result.count >= 10)
        }
        assertEquals(FinancialEvidenceViewport(0, 3), financialEvidenceViewport(3, 1..2))
    }
    @Test fun doesNotClampInvalidReferencesIntoUnrelatedCandles() {
        assertNull(financialEvidenceViewport(0, 0..0))
        assertNull(financialEvidenceViewport(10, -1..2))
        assertNull(financialEvidenceViewport(10, 2..10))
        assertNull(financialEvidenceViewport(10, IntRange.EMPTY))
    }
}
