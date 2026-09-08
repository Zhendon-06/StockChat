package com.guet.liang.stockchat.data

/** Explicit, opt-in example based on the supplied screenshot. Never used as a live security response. */
internal object CapitalFlowDemoData {
    val distribution = listOf(
        CapitalDistributionValue("主力流入", 4.12e8f, true),
        CapitalDistributionValue("主力流出", 4.25e8f, false),
        CapitalDistributionValue("散户流入", 5.72e8f, true),
        CapitalDistributionValue("散户流出", 5.59e8f, false),
    )
    // Local illustrative daily net values, in yuan; not a historical market-data cache.
    val points = listOf(
        "08-12" to -7200000f, "08-13" to -3000000f, "08-14" to -600000f,
        "08-17" to 6200000f, "08-18" to 26000000f, "08-19" to -5700000f,
        "08-20" to 3200000f, "08-21" to -19600000f, "08-24" to 18500000f,
        "08-25" to 82000000f, "08-26" to 85000000f, "08-27" to 42000000f,
        "08-28" to 28000000f, "08-31" to 46000000f, "09-01" to -82000000f,
        "09-02" to -133000000f, "09-03" to 6300000f, "09-04" to -16100000f,
        "09-07" to -17400000f, "09-08" to -13000000f,
    ).map { (date, net) -> CapitalFlowPoint("2026-$date", net, -net * 0.6f, -net * 0.4f, net * 0.45f, net * 0.55f) }
}

internal data class CapitalDistributionValue(val label: String, val amount: Float, val inflow: Boolean)
