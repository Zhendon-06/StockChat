package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.HistoricalPointsResult
import com.guet.liang.stockchat.model.MarketDataResult
import com.guet.liang.stockchat.model.MarketOrderLevel
import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TencentHistoricalCandle
import com.guet.liang.stockchat.model.TencentHistoricalPoint
import com.guet.liang.stockchat.model.TencentMarketSnapshot
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal class TencentMarketDataService(
    private val networkModule: NetworkModule,
) {
    fun load(
        plan: SecuritiesQueryPlan,
        callback: (MarketDataResult) -> Unit,
    ) {
        loadSnapshots(
            targets = plan.targets.distinctBy(SecurityTarget::providerSymbol),
            needsIntraday = plan.needsIntraday,
            callback = callback,
        )
    }

    fun loadDetail(
        symbol: String,
        callback: (MarketDataResult) -> Unit,
    ) {
        val providerSymbol = normalizeProviderSymbol(symbol)
        if (providerSymbol == null) {
            callback(MarketDataResult.Empty)
            return
        }
        loadSnapshots(
            targets = listOf(SecurityTarget(providerSymbol)),
            needsIntraday = true,
            callback = callback,
        )
    }

    fun loadHistoricalPoints(
        symbol: String,
        count: Int = DEFAULT_HISTORICAL_POINT_COUNT,
        callback: (HistoricalPointsResult) -> Unit,
    ) {
        val providerSymbol = normalizeProviderSymbol(symbol)
        if (providerSymbol == null) {
            callback(HistoricalPointsResult.Empty)
            return
        }
        val requestedCount = count.coerceIn(MIN_HISTORICAL_POINT_COUNT, MAX_HISTORICAL_POINT_COUNT)
        val params = historicalRequestParams(providerSymbol, requestedCount)
        networkModule.requestGet(TENCENT_KLINE_URL, params) { data, success, errorMessage, response ->
            if (!isSuccessfulMarketResponse(success, response.statusCode)) {
                callback(
                    HistoricalPointsResult.Failure(
                        errorMessage.ifBlank { "腾讯历史行情服务暂时不可用，请稍后重试。" }
                    )
                )
                return@requestGet
            }
            val points = TencentMarketResponseParser.parseHistoricalPoints(
                response = data,
                providerSymbol = providerSymbol,
                maxCount = requestedCount,
            )
            if (points.isEmpty()) {
                callback(HistoricalPointsResult.Empty)
            } else {
                callback(
                    HistoricalPointsResult.Success(
                        providerSymbol = providerSymbol,
                        points = points,
                    )
                )
            }
        }
    }

    /** Loads up to [MAX_CONCURRENT_SNAPSHOTS] targets at a time and reports them in request order. */
    private fun loadSnapshots(
        targets: List<SecurityTarget>,
        needsIntraday: Boolean,
        callback: (MarketDataResult) -> Unit,
    ) {
        if (targets.isEmpty()) {
            callback(MarketDataResult.Empty)
            return
        }
        val results = arrayOfNulls<SnapshotResult>(targets.size)
        var completed = 0
        var nextIndex = 0
        fun finish() {
            val snapshots = mutableListOf<TencentMarketSnapshot>()
            val notices = mutableListOf<String>()
            targets.forEachIndexed { index, target ->
                val label = target.displayName.ifBlank { target.providerSymbol }
                when (val result = results[index]) {
                    is SnapshotResult.Success -> snapshots += result.snapshot
                    SnapshotResult.Empty -> notices += "$label：行情暂无数据。"
                    is SnapshotResult.Failure -> notices += "$label：${result.message}"
                    null -> notices += "$label：行情暂无数据。"
                }
            }
            callback(
                if (snapshots.isEmpty()) {
                    if (notices.isEmpty()) MarketDataResult.Empty else MarketDataResult.Failure(notices.joinToString("\n"))
                } else {
                    MarketDataResult.Success(snapshots, notices)
                }
            )
        }
        fun startNext() {
            val index = nextIndex
            if (index >= targets.size) return
            nextIndex++
            loadSnapshot(targets[index], needsIntraday) { result ->
                results[index] = result
                completed++
                if (completed == targets.size) finish() else startNext()
            }
        }
        repeat(minOf(MAX_CONCURRENT_SNAPSHOTS, targets.size)) { startNext() }
    }

    private fun loadSnapshot(
        target: SecurityTarget,
        needsIntraday: Boolean,
        callback: (SnapshotResult) -> Unit,
    ) {
        val params = JSONObject().apply {
            put("param", "${target.providerSymbol},day,,,20,qfq")
        }
        networkModule.requestGet(TENCENT_KLINE_URL, params) { data, success, errorMessage, response ->
            if (!isSuccessfulMarketResponse(success, response.statusCode)) {
                callback(
                    SnapshotResult.Failure(
                        errorMessage.ifBlank { "腾讯行情服务暂时不可用，请稍后重试。" }
                    )
                )
                return@requestGet
            }
            val snapshot = TencentMarketResponseParser.parseSnapshot(data, target.providerSymbol)
            if (snapshot == null) {
                callback(SnapshotResult.Empty)
                return@requestGet
            }
            if (!needsIntraday) {
                callback(SnapshotResult.Success(snapshot))
                return@requestGet
            }
            loadIntraday(snapshot, callback)
        }
    }

    private fun loadIntraday(
        snapshot: TencentMarketSnapshot,
        callback: (SnapshotResult) -> Unit,
    ) {
        val params = JSONObject().apply {
            put("code", snapshot.providerSymbol)
        }
        networkModule.requestGet(TENCENT_MINUTE_URL, params) { data, success, _, response ->
            if (!isSuccessfulMarketResponse(success, response.statusCode)) {
                callback(SnapshotResult.Success(snapshot))
                return@requestGet
            }
            val intradayPoints = TencentMarketResponseParser.parseMinutePoints(
                data,
                snapshot.providerSymbol,
            )
            callback(
                SnapshotResult.Success(
                    if (intradayPoints.size > 1) {
                        snapshot.copy(
                            quote = snapshot.quote.copy(trendPoints = intradayPoints),
                        )
                    } else {
                        snapshot
                    }
                )
            )
        }
    }

    private sealed class SnapshotResult {
        data class Success(val snapshot: TencentMarketSnapshot) : SnapshotResult()
        data object Empty : SnapshotResult()
        data class Failure(val message: String) : SnapshotResult()
    }

    private companion object {
        const val MAX_CONCURRENT_SNAPSHOTS = 6
    }
}
