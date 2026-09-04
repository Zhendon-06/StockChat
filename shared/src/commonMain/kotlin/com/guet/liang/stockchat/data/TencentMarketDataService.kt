package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

private const val MIN_HISTORICAL_POINT_COUNT = 1
private const val MAX_HISTORICAL_POINT_COUNT = 240
private const val DEFAULT_HISTORICAL_POINT_COUNT = 120

internal data class TencentMarketSnapshot(
    val providerSymbol: String,
    val quote: StockQuote,
    val previousClose: String,
    val open: String,
    val high: String,
    val low: String,
    val volume: String,
    val volumeUnit: String,
    val amount: String,
    val amountUnit: String,
    val turnoverRate: String,
    val priceEarningsRatio: String,
    val amplitude: String,
)

internal sealed class MarketDataResult {
    data class Success(val snapshots: List<TencentMarketSnapshot>) : MarketDataResult()
    data object Empty : MarketDataResult()
    data class Failure(val message: String) : MarketDataResult()
}

internal data class TencentHistoricalPoint(
    val date: String,
    val close: Float,
)

internal sealed class HistoricalPointsResult {
    data class Success(
        val providerSymbol: String,
        val points: List<TencentHistoricalPoint>,
    ) : HistoricalPointsResult()

    data object Empty : HistoricalPointsResult()
    data class Failure(val message: String) : HistoricalPointsResult()
}

internal class TencentMarketDataService(
    private val networkModule: NetworkModule,
) {
    fun load(
        plan: SecuritiesQueryPlan,
        callback: (MarketDataResult) -> Unit,
    ) {
        resolveTargets(plan.targets, plan.unresolvedTerms) { targetResult ->
            when (targetResult) {
                is TargetResolutionResult.Success -> loadSnapshots(
                    targets = targetResult.targets,
                    needsIntraday = plan.needsIntraday,
                    callback = callback,
                )
                TargetResolutionResult.Empty -> callback(MarketDataResult.Empty)
                is TargetResolutionResult.Failure -> callback(
                    MarketDataResult.Failure(targetResult.message)
                )
            }
        }
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
        val params = JSONObject().apply {
            put("param", "$providerSymbol,day,,,$requestedCount,qfq")
        }
        networkModule.requestGet(KLINE_URL, params) { data, success, errorMessage, response ->
            if (!isSuccessful(success, response.statusCode)) {
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

    private fun resolveTargets(
        initialTargets: List<SecurityTarget>,
        searchTerms: List<String>,
        callback: (TargetResolutionResult) -> Unit,
    ) {
        if (searchTerms.isEmpty()) {
            callback(
                if (initialTargets.isEmpty()) {
                    TargetResolutionResult.Empty
                } else {
                    TargetResolutionResult.Success(initialTargets.distinctBy(SecurityTarget::providerSymbol))
                }
            )
            return
        }
        val targets = initialTargets.toMutableList()
        fun resolveAt(index: Int) {
            if (index >= searchTerms.size) {
                callback(
                    if (targets.isEmpty()) {
                        TargetResolutionResult.Empty
                    } else {
                        TargetResolutionResult.Success(targets.distinctBy(SecurityTarget::providerSymbol))
                    }
                )
                return
            }
            search(searchTerms[index]) { result ->
                when (result) {
                    is SearchResult.Success -> {
                        targets += result.target
                        resolveAt(index + 1)
                    }
                    SearchResult.Empty -> callback(TargetResolutionResult.Empty)
                    is SearchResult.Failure -> callback(TargetResolutionResult.Failure(result.message))
                }
            }
        }
        resolveAt(0)
    }

    private fun search(
        term: String,
        callback: (SearchResult) -> Unit,
    ) {
        val params = JSONObject().apply {
            put("t", "all")
            put("q", term)
        }
        networkModule.requestGet(SEARCH_URL, params) { data, success, errorMessage, response ->
            if (!isSuccessful(success, response.statusCode)) {
                callback(
                    SearchResult.Failure(
                        errorMessage.ifBlank { "证券搜索服务暂时不可用，请输入六位代码后重试。" }
                    )
                )
                return@requestGet
            }
            val matches = TencentMarketResponseParser.parseSearch(data.optString("data"))
            val normalizedTerm = term.trim()
            val exactMatches = matches.filter { match ->
                match.code.equals(normalizedTerm, ignoreCase = true) ||
                    match.name.equals(normalizedTerm, ignoreCase = true)
            }
            val selected = (exactMatches.ifEmpty { matches }).firstOrNull()
            if (selected == null) {
                callback(SearchResult.Empty)
            } else {
                callback(
                    SearchResult.Success(
                        SecurityTarget(
                            providerSymbol = selected.providerSymbol,
                            displayName = selected.name,
                        )
                    )
                )
            }
        }
    }

    private fun loadSnapshots(
        targets: List<SecurityTarget>,
        needsIntraday: Boolean,
        callback: (MarketDataResult) -> Unit,
    ) {
        val snapshots = mutableListOf<TencentMarketSnapshot>()
        fun loadAt(index: Int) {
            if (index >= targets.size) {
                callback(
                    if (snapshots.isEmpty()) {
                        MarketDataResult.Empty
                    } else {
                        MarketDataResult.Success(snapshots)
                    }
                )
                return
            }
            loadSnapshot(targets[index], needsIntraday) { result ->
                when (result) {
                    is SnapshotResult.Success -> {
                        snapshots += result.snapshot
                        loadAt(index + 1)
                    }
                    SnapshotResult.Empty -> callback(MarketDataResult.Empty)
                    is SnapshotResult.Failure -> callback(MarketDataResult.Failure(result.message))
                }
            }
        }
        loadAt(0)
    }

    private fun loadSnapshot(
        target: SecurityTarget,
        needsIntraday: Boolean,
        callback: (SnapshotResult) -> Unit,
    ) {
        val params = JSONObject().apply {
            put("param", "${target.providerSymbol},day,,,20,qfq")
        }
        networkModule.requestGet(KLINE_URL, params) { data, success, errorMessage, response ->
            if (!isSuccessful(success, response.statusCode)) {
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
        networkModule.requestGet(MINUTE_URL, params) { data, success, _, response ->
            if (!isSuccessful(success, response.statusCode)) {
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

    private fun isSuccessful(success: Boolean, statusCode: Int?): Boolean {
        return success && (statusCode == null || statusCode in 200..299)
    }

    private sealed class TargetResolutionResult {
        data class Success(val targets: List<SecurityTarget>) : TargetResolutionResult()
        data object Empty : TargetResolutionResult()
        data class Failure(val message: String) : TargetResolutionResult()
    }

    private sealed class SearchResult {
        data class Success(val target: SecurityTarget) : SearchResult()
        data object Empty : SearchResult()
        data class Failure(val message: String) : SearchResult()
    }

    private sealed class SnapshotResult {
        data class Success(val snapshot: TencentMarketSnapshot) : SnapshotResult()
        data object Empty : SnapshotResult()
        data class Failure(val message: String) : SnapshotResult()
    }

    companion object {
        private const val SEARCH_URL = "https://smartbox.gtimg.cn/s3/"
        private const val KLINE_URL =
            "https://proxy.finance.qq.com/ifzqgtimg/appstock/app/newfqkline/get"
        private const val MINUTE_URL =
            "https://web.ifzq.gtimg.cn/appstock/app/minute/query"
    }
}

internal object TencentMarketResponseParser {
    fun parseSnapshot(
        response: JSONObject,
        providerSymbol: String,
    ): TencentMarketSnapshot? {
        if (response.optInt("code", -1) != 0) {
            return null
        }
        val securityData = response.optJSONObject("data")
            ?.optJSONObject(providerSymbol)
            ?: return null
        val quoteData = securityData.optJSONObject("qt")
            ?.optJSONArray(providerSymbol)
            ?: return null
        val name = quoteData.optString(1).orEmpty().trim()
        val code = quoteData.optString(2).orEmpty().trim()
        val price = quoteData.optString(3).orEmpty().trim()
        if (name.isEmpty() || code.isEmpty() || price.isEmpty()) {
            return null
        }

        val previousClose = quoteData.optString(4).orEmpty().trim()
        val open = quoteData.optString(5).orEmpty().trim()
        val volume = quoteData.optString(6).orEmpty().trim()
        val updatedAt = quoteData.optString(30).orEmpty().trim()
        val rawChange = quoteData.optString(31).orEmpty().trim()
        val rawChangePercent = quoteData.optString(32).orEmpty().trim()
        val high = quoteData.optString(33).orEmpty().trim()
        val low = quoteData.optString(34).orEmpty().trim()
        val amount = quoteData.optString(37).orEmpty().trim()
        val turnoverRate = quoteData.optString(38).orEmpty().trim()
        val priceEarningsRatio = quoteData.optString(39).orEmpty().trim()
        val amplitude = quoteData.optString(43).orEmpty().trim()
        val isHongKong = providerSymbol.startsWith("hk")
        val isIndex = !isHongKong && securityData.optJSONArray("qfqday") == null &&
            securityData.optJSONArray("day") != null
        val volumeUnit = if (isHongKong) "股" else "手"
        val amountUnit = if (isHongKong) "港元" else "万元"
        val trendPoints = parseKlinePoints(securityData)
        val numericChange = rawChange.toDoubleOrNull() ?: 0.0
        val change = signedValue(rawChange, numericChange)
        val changePercent = signedValue(rawChangePercent, numericChange) + "%"
        val movement = when {
            numericChange > 0.0 -> "上涨"
            numericChange < 0.0 -> "下跌"
            else -> "持平"
        }
        val quote = StockQuote(
            name = name,
            symbol = code,
            marketLabel = marketLabel(providerSymbol, isIndex),
            price = price,
            change = change,
            changePercent = changePercent,
            updatedAt = "腾讯行情 · ${formatTimestamp(updatedAt)}",
            isPositive = numericChange >= 0.0,
            trendPoints = trendPoints.ifEmpty {
                listOfNotNull(price.toFloatOrNull())
            },
            summary = buildSummary(
                previousClose,
                open,
                high,
                low,
                volume,
                volumeUnit,
                amount,
                amountUnit,
            ),
            aiInsight = "最新行情快照显示该标的当前${movement}${changePercent}。请结合基本面、估值和风险承受能力综合判断。",
        )
        return TencentMarketSnapshot(
            providerSymbol = providerSymbol,
            quote = quote,
            previousClose = previousClose,
            open = open,
            high = high,
            low = low,
            volume = volume,
            volumeUnit = volumeUnit,
            amount = amount,
            amountUnit = amountUnit,
            turnoverRate = turnoverRate,
            priceEarningsRatio = priceEarningsRatio,
            amplitude = amplitude,
        )
    }

    fun parseMinutePoints(
        response: JSONObject,
        providerSymbol: String,
    ): List<Float> {
        if (response.optInt("code", -1) != 0) {
            return emptyList()
        }
        val minuteData = response.optJSONObject("data")
            ?.optJSONObject(providerSymbol)
            ?.optJSONObject("data")
            ?.optJSONArray("data")
            ?: return emptyList()
        val points = buildList {
            for (index in 0 until minuteData.length()) {
                minuteData.optString(index).orEmpty()
                    .trim()
                    .split(Regex("\\s+"))
                    .getOrNull(1)
                    ?.toFloatOrNull()
                    ?.let(::add)
            }
        }
        return samplePoints(points, MAX_CHART_POINTS)
    }

    fun parseHistoricalPoints(
        response: JSONObject,
        providerSymbol: String,
        maxCount: Int = DEFAULT_HISTORICAL_POINT_COUNT,
    ): List<TencentHistoricalPoint> {
        if (response.optInt("code", -1) != 0 || maxCount <= 0) {
            return emptyList()
        }
        val securityData = response.optJSONObject("data")
            ?.optJSONObject(providerSymbol)
            ?: return emptyList()
        val rows = securityData.optJSONArray("qfqday")
            ?.takeIf { it.length() > 0 }
            ?: securityData.optJSONArray("day")?.takeIf { it.length() > 0 }
            ?: return emptyList()
        val points = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONArray(index) ?: continue
                val date = row.optString(0).orEmpty().trim()
                val close = row.optString(2).orEmpty().trim().toFloatOrNull()
                if (date.isNotEmpty() && close != null && close.isFinite() && close > 0f) {
                    add(TencentHistoricalPoint(date = date, close = close))
                }
            }
        }
        val orderedPoints = points
            .map { point ->
                point.copy(date = point.date.replace('/', '-'))
            }
            .distinctBy(TencentHistoricalPoint::date)
            .sortedBy(TencentHistoricalPoint::date)
        return sampleHistoricalPoints(orderedPoints, maxCount)
    }

    fun parseSearch(rawResponse: String): List<TencentSearchMatch> {
        val payload = rawResponse.substringAfter("=\"", "")
            .substringBeforeLast("\"", "")
        if (payload.isEmpty()) {
            return emptyList()
        }
        return payload.split('^').mapNotNull { item ->
            val fields = item.split('~')
            val market = fields.getOrNull(0)?.lowercase().orEmpty()
            val code = fields.getOrNull(1).orEmpty()
            val name = decodeUnicodeEscapes(fields.getOrNull(2).orEmpty())
            val validCode = when (market) {
                "hk" -> code.length == 5
                "sh", "sz", "bj" -> code.length == 6
                else -> false
            }
            if (!validCode || name.isBlank()) {
                null
            } else {
                TencentSearchMatch(
                    providerSymbol = market + code,
                    code = code,
                    name = name,
                    type = fields.getOrNull(4).orEmpty(),
                )
            }
        }
    }

    private fun parseKlinePoints(securityData: JSONObject): List<Float> {
        val rows = securityData.optJSONArray("qfqday")
            ?: securityData.optJSONArray("day")
            ?: return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                rows.optJSONArray(index)
                    ?.optString(2)
                    ?.toFloatOrNull()
                    ?.let(::add)
            }
        }
    }

    private fun signedValue(rawValue: String, numericChange: Double): String {
        if (rawValue.isEmpty()) {
            return if (numericChange >= 0.0) "+0.00" else "0.00"
        }
        return if (numericChange > 0.0 && !rawValue.startsWith("+")) {
            "+$rawValue"
        } else {
            rawValue
        }
    }

    private fun marketLabel(providerSymbol: String, isIndex: Boolean): String {
        val marketName = when {
            providerSymbol.startsWith("sh") -> "沪市"
            providerSymbol.startsWith("sz") -> "深市"
            providerSymbol.startsWith("bj") -> "北交所"
            providerSymbol.startsWith("hk") -> "港股"
            else -> "证券"
        }
        return if (isIndex) {
            "${marketName}指数 · 腾讯行情"
        } else {
            "$marketName · 腾讯行情"
        }
    }

    private fun buildSummary(
        previousClose: String,
        open: String,
        high: String,
        low: String,
        volume: String,
        volumeUnit: String,
        amount: String,
        amountUnit: String,
    ): String {
        return buildList {
            previousClose.takeIf(String::isNotEmpty)?.let { add("昨收 $it") }
            open.takeIf(String::isNotEmpty)?.let { add("今开 $it") }
            high.takeIf(String::isNotEmpty)?.let { add("最高 $it") }
            low.takeIf(String::isNotEmpty)?.let { add("最低 $it") }
            volume.takeIf(String::isNotEmpty)?.let { add("成交量 $it $volumeUnit") }
            amount.takeIf(String::isNotEmpty)?.let { add("成交额 $it $amountUnit") }
        }.joinToString("，")
    }

    private fun formatTimestamp(timestamp: String): String {
        if (
            timestamp.length >= 19 && timestamp[4] in setOf('/', '-') &&
            timestamp[7] in setOf('/', '-')
        ) {
            return timestamp.take(19).replace('/', '-')
        }
        if (timestamp.length < 14) {
            return timestamp.ifBlank { "时间未知" }
        }
        return "${timestamp.substring(0, 4)}-${timestamp.substring(4, 6)}-" +
            "${timestamp.substring(6, 8)} ${timestamp.substring(8, 10)}:" +
            "${timestamp.substring(10, 12)}:${timestamp.substring(12, 14)}"
    }

    private fun decodeUnicodeEscapes(value: String): String {
        val result = StringBuilder()
        var index = 0
        while (index < value.length) {
            if (index + 5 < value.length && value[index] == '\\' && value[index + 1] == 'u') {
                val codePoint = value.substring(index + 2, index + 6).toIntOrNull(16)
                if (codePoint != null) {
                    result.append(codePoint.toChar())
                    index += 6
                    continue
                }
            }
            result.append(value[index])
            index += 1
        }
        return result.toString()
    }

    private fun samplePoints(points: List<Float>, maxCount: Int): List<Float> {
        if (maxCount <= 0 || points.isEmpty()) {
            return emptyList()
        }
        if (maxCount == 1) {
            return listOf(points.last())
        }
        if (points.size <= maxCount) {
            return points
        }
        return List(maxCount) { index ->
            val sourceIndex = index * (points.lastIndex).toFloat() / (maxCount - 1).toFloat()
            points[sourceIndex.toInt().coerceIn(points.indices)]
        }
    }

    private fun sampleHistoricalPoints(
        points: List<TencentHistoricalPoint>,
        maxCount: Int,
    ): List<TencentHistoricalPoint> {
        if (maxCount <= 0 || points.isEmpty()) {
            return emptyList()
        }
        if (maxCount == 1) {
            return listOf(points.last())
        }
        if (points.size <= maxCount) {
            return points
        }
        return List(maxCount) { index ->
            val sourceIndex = index * (points.lastIndex).toFloat() / (maxCount - 1).toFloat()
            points[sourceIndex.toInt().coerceIn(points.indices)]
        }
    }

    private const val MAX_CHART_POINTS = 80
}

internal data class TencentSearchMatch(
    val providerSymbol: String,
    val code: String,
    val name: String,
    val type: String,
)

internal fun normalizeProviderSymbol(symbol: String): String? {
    val normalized = symbol.trim().lowercase().replace(" ", "")
    Regex("^(sh|sz|bj)\\d{6}$").matchEntire(normalized)?.let {
        return normalized
    }
    Regex("^hk(\\d{1,5})$").matchEntire(normalized)?.let { match ->
        return "hk${match.groupValues[1].padStart(5, '0')}"
    }
    Regex("^(\\d{6})[.]?(sh|sz|bj)$").matchEntire(normalized)?.let { match ->
        return match.groupValues[2] + match.groupValues[1]
    }
    Regex("^(\\d{1,5})[.]?hk$").matchEntire(normalized)?.let { match ->
        return "hk${match.groupValues[1].padStart(5, '0')}"
    }
    if (!Regex("^\\d{6}$").matches(normalized)) {
        return null
    }
    return when (normalized.first()) {
        '4', '8' -> "bj$normalized"
        '5', '6', '9' -> "sh$normalized"
        else -> "sz$normalized"
    }
}

internal fun providerSymbolForQuote(quote: StockQuote): String? {
    val market = when {
        quote.marketLabel.startsWith("沪市") -> "sh"
        quote.marketLabel.startsWith("深市") -> "sz"
        quote.marketLabel.startsWith("北交所") -> "bj"
        quote.marketLabel.startsWith("港股") -> "hk"
        else -> null
    }
    return market?.let { "$it${quote.symbol}" } ?: normalizeProviderSymbol(quote.symbol)
}
