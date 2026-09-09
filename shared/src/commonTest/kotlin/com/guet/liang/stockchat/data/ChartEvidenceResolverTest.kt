@file:Suppress("MagicNumber", "LongParameterList", "MaxLineLength")
package com.guet.liang.stockchat.data

import com.guet.liang.kuiklychart.finance.FinancialPoint
import com.guet.liang.stockchat.model.ChartEvidenceReference
import com.guet.liang.stockchat.model.ChartEvidenceResolution
import com.guet.liang.stockchat.model.MarketPeriod
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import kotlin.test.*

class ChartEvidenceResolverTest {
    private val dates = listOf("2026-09-02", "2026-09-03", "2026-09-04", "2026-09-07", "2026-09-08")
    private val points = dates.mapIndexed { index, date ->
        val price = 10f + index
        FinancialPoint(date, price, price, price, price, 100f)
    }
    private val reference = ChartEvidenceReference("600519", "day", dates[1], dates[4], "close", "snapshot-1")
    private fun resolve(ref: ChartEvidenceReference? = reference, data: List<FinancialPoint> = points) =
        ChartEvidenceResolver.resolve(ref, "600519", MarketPeriod.DAY, "snapshot-1", data)

    @Test fun matchesExactTradingDatesIncludingWeekendsInsideRange() {
        val result = assertIs<ChartEvidenceResolution.Valid>(resolve())
        assertEquals(1..4, result.indices)
        assertTrue(result.label.contains("4 根 K 线"))
        assertEquals(2..2, assertIs<ChartEvidenceResolution.Valid>(resolve(reference.copy(startDate = dates[2], endDate = dates[2]))).indices)
    }

    @Test fun refusesMissingWeekendFutureAndReversedEndpoints() {
        listOf(
            reference.copy(startDate = "2026-09-05"),
            reference.copy(startDate = "2026-09-01"),
            reference.copy(endDate = "2026-09-09"),
            reference.copy(startDate = dates.last(), endDate = dates.first()),
            reference.copy(startDate = "2026-02-30"),
            reference.copy(startDate = "最近五天"),
        ).forEach { assertIs<ChartEvidenceResolution.Invalid>(resolve(it)) }
    }

    @Test fun refusesWrongIdentityPeriodVersionAndUnknownMetric() {
        listOf(
            reference.copy(symbol = "000001"), reference.copy(period = "week"),
            reference.copy(sourceUpdatedAt = "snapshot-0"), reference.copy(metric = "RSI"),
        ).forEach { assertIs<ChartEvidenceResolution.Invalid>(resolve(it)) }
        assertIs<ChartEvidenceResolution.Invalid>(ChartEvidenceResolver.resolve(reference.copy(period = "minute"),
            "600519", MarketPeriod.INTRADAY, "snapshot-1", points))
        assertIs<ChartEvidenceResolution.Invalid>(resolve(null))
    }

    @Test fun refusesMalformedSeriesInsteadOfShiftingIndices() {
        listOf(emptyList(), points.reversed(), points + points.last(),
            points.mapIndexed { i, point -> if (i == 0) point.copy(close = Float.NaN) else point },
        ).forEach { assertIs<ChartEvidenceResolution.Invalid>(resolve(data = it)) }
    }

    @Test fun checksAvailabilityOfVolumeAndMovingAverageWarmup() {
        assertIs<ChartEvidenceResolution.Valid>(resolve(reference.copy(metric = "volume")))
        assertIs<ChartEvidenceResolution.Invalid>(resolve(reference.copy(metric = "volume"), points.map { it.copy(volume = null) }))
        assertIs<ChartEvidenceResolution.Invalid>(resolve(reference.copy(metric = "MA5")))
        assertIs<ChartEvidenceResolution.Valid>(resolve(reference.copy(metric = "MA5", startDate = dates.last())))
    }

    @Test fun parserNeverFindsReferencesInProseAndKeepsInvalidReferencesInert() {
        val parsed = parseChartConclusions(JSONArray("""[
            {"text":"2026-09-02 至 2026-09-08 回升"},
            {"text":"回升", "reference":{"symbol":"600519", "period":"day", "startDate":"2026-09-03", "endDate":"2026-09-08", "metric":"close", "sourceUpdatedAt":"snapshot-1"}},
            {"text":"无法核验", "reference":{"startDate":"昨天"}},
            {"text":""}, 42
        ]"""))
        assertEquals(3, parsed.size)
        assertNull(parsed[0].reference)
        assertIs<ChartEvidenceResolution.Valid>(resolve(parsed[1].reference))
        assertIs<ChartEvidenceResolution.Invalid>(resolve(parsed[2].reference))
        assertTrue(parseChartConclusions(null).isEmpty())
    }

    @Test fun demoEvidenceUsesSelectedHistoryAndActualMovement() {
        val latest = assertNotNull(marketDemoConclusion("600519", MarketPeriod.DAY, "snapshot-1", points, null))
        assertTrue(latest.text.contains("回升"))
        assertEquals(0..4, assertIs<ChartEvidenceResolution.Valid>(resolve(latest.reference)).indices)
        val selected = assertNotNull(marketDemoConclusion("600519", MarketPeriod.DAY, "snapshot-1", points, 2))
        assertEquals(0..2, assertIs<ChartEvidenceResolution.Valid>(resolve(selected.reference)).indices)
        val falling = assertNotNull(marketDemoConclusion("600519", MarketPeriod.DAY, "snapshot-1", points.mapIndexed { i, p ->
            p.copy(open = 20f - i, high = 20f - i, low = 20f - i, close = 20f - i)
        }, null))
        assertTrue(falling.text.contains("回落"))
        assertNull(marketDemoConclusion("600519", MarketPeriod.INTRADAY, "snapshot-1", points, null))
    }
}
