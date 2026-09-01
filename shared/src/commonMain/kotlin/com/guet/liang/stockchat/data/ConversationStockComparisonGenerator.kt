package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.ConversationStockComparisonRow
import com.guet.liang.stockchat.model.ConversationStockComparisonSnapshot
import com.guet.liang.stockchat.model.ConversationStockDataSource
import com.guet.liang.stockchat.model.ConversationTableArtifactSnapshot
import com.guet.liang.stockchat.model.ConversationTableRow
import com.guet.liang.stockchat.model.ConversationTableRowStatus
import com.guet.liang.stockchat.model.StockQuote

internal object ConversationStockComparisonGenerator {
    fun generate(
        title: String,
        messages: List<ChatMessage>,
    ): ConversationStockComparisonSnapshot {
        val rowsByIdentity = linkedMapOf<String, ConversationStockComparisonRow>()
        messages.forEach { message ->
            message.blocks.forEach { block ->
                when (block) {
                    is AnswerBlock.Markdown -> {
                        val text = block.source.ifBlank { block.fallbackText }
                        detectSecurities(text).forEach { identity ->
                            mergeMention(rowsByIdentity, identity, message)
                        }
                    }
                    is AnswerBlock.MarketQuote -> mergeQuote(
                        rowsByIdentity = rowsByIdentity,
                        quote = block.quote,
                        message = message,
                    )
                    is AnswerBlock.ImageGallery -> Unit
                }
            }
        }
        return ConversationStockComparisonSnapshot(
            title = normalizedTitle(title),
            sourceMessageCount = messages.size,
            rows = rowsByIdentity.values.toList(),
        )
    }

    fun overlay(
        snapshot: ConversationStockComparisonSnapshot,
        marketSnapshots: List<TencentMarketSnapshot>,
    ): ConversationStockComparisonSnapshot {
        if (marketSnapshots.isEmpty() || snapshot.rows.isEmpty()) {
            return snapshot
        }
        val snapshotsByProvider = marketSnapshots.associateBy {
            it.providerSymbol.trim().lowercase()
        }
        return snapshot.copy(
            rows = snapshot.rows.map { row ->
                val marketSnapshot = snapshotsByProvider[row.providerSymbol.lowercase()]
                    ?: marketSnapshots.singleOrNull { candidate ->
                        candidate.quote.symbol.equals(row.symbol, ignoreCase = true)
                    }
                marketSnapshot?.let { row.withMarketSnapshot(it) } ?: row
            },
        )
    }

    fun generateArtifact(
        title: String,
        messages: List<ChatMessage>,
    ): ConversationTableArtifactSnapshot {
        return toArtifactSnapshot(generate(title, messages))
    }

    fun toArtifactSnapshot(
        snapshot: ConversationStockComparisonSnapshot,
    ): ConversationTableArtifactSnapshot {
        return ConversationTableArtifactSnapshot(
            title = snapshot.title,
            sourceMessageCount = snapshot.sourceMessageCount,
            rows = snapshot.rows.mapIndexed { index, row ->
                ConversationTableRow(
                    sequence = index + 1,
                    userQuestion = row.sourceDescription,
                    aiAnswerSummary = row.marketMetricSummary(),
                    relatedInstrument = "${row.displayName}（${row.symbol}）",
                    status = if (row.hasQuote) {
                        ConversationTableRowStatus.COMPLETED
                    } else {
                        ConversationTableRowStatus.WAITING
                    },
                )
            },
        )
    }

    private fun mergeMention(
        rowsByIdentity: MutableMap<String, ConversationStockComparisonRow>,
        identity: SecurityIdentity,
        message: ChatMessage,
    ) {
        val key = identity.key
        val existing = rowsByIdentity[key] ?: identity.toComparisonRow()
        rowsByIdentity[key] = existing.copy(
            providerSymbol = existing.providerSymbol.ifBlank { identity.providerSymbol },
            name = existing.name.ifBlank { identity.name },
            symbol = existing.symbol.ifBlank { identity.symbol },
            marketLabel = existing.marketLabel.ifBlank { identity.marketLabel },
            mentionedByUser = existing.mentionedByUser || message.role == ChatRole.USER,
            generatedByAi = existing.generatedByAi || message.role == ChatRole.ASSISTANT,
            relatedMessageIds = (existing.relatedMessageIds + message.id).distinct(),
        )
    }

    private fun mergeQuote(
        rowsByIdentity: MutableMap<String, ConversationStockComparisonRow>,
        quote: StockQuote,
        message: ChatMessage,
    ) {
        val identity = identityForQuote(quote)
        val key = identity.key
        val existing = rowsByIdentity[key] ?: identity.toComparisonRow()
        val summaryMetrics = parseSummaryMetrics(quote.summary)
        rowsByIdentity[key] = existing.copy(
            providerSymbol = identity.providerSymbol.ifBlank { existing.providerSymbol },
            name = quote.name.ifBlank { existing.name },
            symbol = quote.symbol.normalizedDisplaySymbol().ifBlank { existing.symbol },
            marketLabel = quote.marketLabel.ifBlank { existing.marketLabel },
            price = quote.price,
            change = quote.change,
            changePercent = quote.changePercent,
            previousClose = summaryMetrics.previousClose,
            open = summaryMetrics.open,
            high = summaryMetrics.high,
            low = summaryMetrics.low,
            volume = summaryMetrics.volume,
            volumeUnit = summaryMetrics.volumeUnit,
            amount = summaryMetrics.amount,
            amountUnit = summaryMetrics.amountUnit,
            turnoverRate = summaryMetrics.turnoverRate,
            priceEarningsRatio = summaryMetrics.priceEarningsRatio,
            amplitude = summaryMetrics.amplitude,
            updatedAt = quote.updatedAt,
            trendPoints = quote.trendPoints,
            summary = quote.summary,
            aiInsight = quote.aiInsight,
            mentionedByUser = existing.mentionedByUser || message.role == ChatRole.USER,
            generatedByAi = existing.generatedByAi || message.role == ChatRole.ASSISTANT,
            dataSource = ConversationStockDataSource.CONVERSATION_QUOTE,
            relatedMessageIds = (existing.relatedMessageIds + message.id).distinct(),
        )
    }

    private fun ConversationStockComparisonRow.withMarketSnapshot(
        snapshot: TencentMarketSnapshot,
    ): ConversationStockComparisonRow {
        val quote = snapshot.quote
        return copy(
            providerSymbol = snapshot.providerSymbol.lowercase(),
            name = quote.name.ifBlank { name },
            symbol = quote.symbol.ifBlank { symbol },
            marketLabel = quote.marketLabel.ifBlank { marketLabel },
            price = quote.price,
            change = quote.change,
            changePercent = quote.changePercent,
            previousClose = snapshot.previousClose,
            open = snapshot.open,
            high = snapshot.high,
            low = snapshot.low,
            volume = snapshot.volume,
            volumeUnit = snapshot.volumeUnit,
            amount = snapshot.amount,
            amountUnit = snapshot.amountUnit,
            turnoverRate = snapshot.turnoverRate,
            priceEarningsRatio = snapshot.priceEarningsRatio,
            amplitude = snapshot.amplitude,
            updatedAt = quote.updatedAt,
            trendPoints = quote.trendPoints,
            summary = quote.summary,
            aiInsight = quote.aiInsight,
            dataSource = ConversationStockDataSource.FRESH_MARKET,
        )
    }

    private fun detectSecurities(text: String): List<SecurityIdentity> {
        if (text.isBlank()) {
            return emptyList()
        }
        val matches = mutableListOf<DetectedSecurity>()
        taggedSecurityRegex.findAll(text).forEach { match ->
            normalizeProviderSymbol(match.groupValues[1])?.let { providerSymbol ->
                matches += DetectedSecurity(
                    index = match.range.first,
                    identity = identityForProvider(
                        providerSymbol = providerSymbol,
                        preferredName = match.groupValues[2].trim(),
                    ),
                )
            }
        }
        knownSecurities.forEach { security ->
            security.aliases.forEach { alias ->
                val index = text.indexOf(alias, ignoreCase = true)
                if (index >= 0) {
                    matches += DetectedSecurity(index, security.toIdentity())
                }
            }
        }
        providerSymbolRegex.findAll(text).forEach { match ->
            val symbolGroup = match.groups[1] ?: return@forEach
            normalizeProviderSymbol(symbolGroup.value)?.let { providerSymbol ->
                matches += DetectedSecurity(
                    match.range.first,
                    identityForProvider(providerSymbol),
                )
            }
        }
        suffixSymbolRegex.findAll(text).forEach { match ->
            val symbolGroup = match.groups[1] ?: return@forEach
            normalizeProviderSymbol(symbolGroup.value)?.let { providerSymbol ->
                matches += DetectedSecurity(
                    match.range.first,
                    identityForProvider(providerSymbol),
                )
            }
        }
        plainSymbolRegex.findAll(text).forEach { match ->
            val hasDigitBefore = match.range.first > 0 && text[match.range.first - 1].isDigit()
            val hasDigitAfter = match.range.last < text.lastIndex && text[match.range.last + 1].isDigit()
            if (
                hasDigitBefore || hasDigitAfter ||
                isPartOfExplicitMarketSymbol(text, match) ||
                isMetricValue(text, match) ||
                isDecimalValue(text, match)
            ) {
                return@forEach
            }
            providerSymbolForBareCode(match.value)?.let { providerSymbol ->
                matches += DetectedSecurity(match.range.first, identityForProvider(providerSymbol))
            }
        }
        val detectedByKey = linkedMapOf<String, SecurityIdentity>()
        matches.sortedBy(DetectedSecurity::index).forEach { match ->
            val existing = detectedByKey[match.identity.key]
            detectedByKey[match.identity.key] = if (existing == null) {
                match.identity
            } else {
                existing.copy(
                    name = existing.name.ifBlank { match.identity.name },
                    marketLabel = existing.marketLabel.ifBlank { match.identity.marketLabel },
                )
            }
        }
        return detectedByKey.values.toList()
    }

    private fun providerSymbolForBareCode(code: String): String? {
        return knownSecurities.firstOrNull { security ->
            security.providerSymbol.displaySymbol() == code
        }?.providerSymbol ?: normalizeProviderSymbol(code)
    }

    private fun isPartOfExplicitMarketSymbol(
        text: String,
        match: MatchResult,
    ): Boolean {
        val prefix = text.substring(0, match.range.first).takeLast(MARKET_AFFIX_SCAN_LENGTH)
        val suffix = text.substring(match.range.last + 1).take(MARKET_AFFIX_SCAN_LENGTH)
        return marketPrefixBeforeCodeRegex.containsMatchIn(prefix) ||
            marketSuffixAfterCodeRegex.containsMatchIn(suffix)
    }

    private fun isMetricValue(
        text: String,
        match: MatchResult,
    ): Boolean {
        val prefix = text.substring(0, match.range.first).takeLast(METRIC_PREFIX_SCAN_LENGTH)
        return metricValuePrefixRegex.containsMatchIn(prefix)
    }

    private fun isDecimalValue(
        text: String,
        match: MatchResult,
    ): Boolean {
        val decimalPointIndex = match.range.last + 1
        val fractionIndex = decimalPointIndex + 1
        return decimalPointIndex < text.length && text[decimalPointIndex] == '.' &&
            fractionIndex < text.length && text[fractionIndex].isDigit()
    }

    private fun identityForQuote(quote: StockQuote): SecurityIdentity {
        val knownSecurity = knownSecurities.firstOrNull { security ->
            security.aliases.any { alias -> quote.name.contains(alias, ignoreCase = true) }
        }
        val explicitProvider = quote.symbol
            .takeIf { symbol -> symbol.any(Char::isLetter) }
            ?.let(::normalizeProviderSymbol)
        val bareSymbol = quote.symbol.filter(Char::isDigit)
        val marketProvider = marketPrefix(quote.marketLabel)?.let { prefix ->
            normalizeProviderSymbol("$prefix$bareSymbol")
        }
        val providerSymbol = explicitProvider
            ?: knownSecurity?.providerSymbol
            ?: marketProvider
            ?: normalizeProviderSymbol(quote.symbol)
            .orEmpty()
        return SecurityIdentity(
            providerSymbol = providerSymbol,
            name = quote.name.ifBlank { knownSecurity?.name.orEmpty() },
            symbol = quote.symbol.normalizedDisplaySymbol().ifBlank {
                providerSymbol.displaySymbol()
            },
            marketLabel = quote.marketLabel.ifBlank {
                knownSecurity?.marketLabel ?: marketLabelForProvider(providerSymbol)
            },
        )
    }

    private fun identityForProvider(
        providerSymbol: String,
        preferredName: String = "",
    ): SecurityIdentity {
        val normalizedProvider = providerSymbol.lowercase()
        val knownSecurity = knownSecurities.firstOrNull {
            it.providerSymbol == normalizedProvider
        }
        return SecurityIdentity(
            providerSymbol = normalizedProvider,
            name = preferredName.ifBlank { knownSecurity?.name.orEmpty() },
            symbol = normalizedProvider.displaySymbol(),
            marketLabel = knownSecurity?.marketLabel ?: marketLabelForProvider(normalizedProvider),
        )
    }

    private fun SecurityIdentity.toComparisonRow(): ConversationStockComparisonRow {
        return ConversationStockComparisonRow(
            providerSymbol = providerSymbol,
            name = name,
            symbol = symbol,
            marketLabel = marketLabel,
        )
    }

    private fun parseSummaryMetrics(summary: String): SummaryMetrics {
        return SummaryMetrics(
            previousClose = summary.metricValue("昨收"),
            open = summary.metricValue("今开"),
            high = summary.metricValue("最高"),
            low = summary.metricValue("最低"),
            volume = summary.metricValue("成交量"),
            volumeUnit = summary.metricUnit("成交量", setOf("手", "股")),
            amount = summary.metricValue("成交额"),
            amountUnit = summary.metricUnit("成交额", setOf("元", "万元", "亿元", "港元")),
            turnoverRate = summary.metricValue("换手率").removeSuffix("%"),
            priceEarningsRatio = summary.metricValue("市盈率").removeSuffix("倍"),
            amplitude = summary.metricValue("振幅").removeSuffix("%"),
        )
    }

    private fun String.metricValue(label: String): String {
        return Regex("$label\\s*([^\\s，；;]+)")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
    }

    private fun String.metricUnit(label: String, supportedUnits: Set<String>): String {
        val tail = substringAfter(label, "").trimStart()
        if (tail.isEmpty()) {
            return ""
        }
        val tokens = tail.substringBeforeAny('，', '；', ';').split(Regex("\\s+"))
        return tokens.getOrNull(1).orEmpty().takeIf(supportedUnits::contains).orEmpty()
    }

    private fun String.substringBeforeAny(vararg delimiters: Char): String {
        val endIndex = delimiters.map { delimiter -> indexOf(delimiter) }
            .filter { index -> index >= 0 }
            .minOrNull()
            ?: length
        return take(endIndex)
    }

    private fun String.normalizedDisplaySymbol(): String {
        return trim().uppercase().let { value ->
            normalizeProviderSymbol(value)?.displaySymbol() ?: value
        }
    }

    private fun String.displaySymbol(): String {
        return when {
            startsWith("hk") -> drop(2).padStart(5, '0')
            startsWith("sh") || startsWith("sz") || startsWith("bj") -> drop(2)
            else -> this
        }
    }

    private fun marketPrefix(marketLabel: String): String? {
        return when {
            marketLabel.startsWith("沪市") -> "sh"
            marketLabel.startsWith("深市") -> "sz"
            marketLabel.startsWith("北交所") -> "bj"
            marketLabel.startsWith("港股") -> "hk"
            else -> null
        }
    }

    private fun marketLabelForProvider(providerSymbol: String): String {
        return when {
            providerSymbol.startsWith("sh") -> "沪市"
            providerSymbol.startsWith("sz") -> "深市"
            providerSymbol.startsWith("bj") -> "北交所"
            providerSymbol.startsWith("hk") -> "港股"
            else -> ""
        }
    }

    private fun normalizedTitle(title: String): String {
        val baseTitle = title.trim()
            .replace(WHITESPACE_REGEX, " ")
            .removeSuffix(TABLE_ARTIFACT_TITLE_SUFFIX)
            .removeSuffix(COMPARISON_TITLE_SUFFIX)
            .trim()
            .take(MAX_TITLE_LENGTH)
            .ifBlank { DEFAULT_TITLE }
        return "$baseTitle$COMPARISON_TITLE_SUFFIX"
    }

    private fun ConversationStockComparisonRow.marketMetricSummary(): String {
        if (!hasQuote) {
            return "待获取最新行情"
        }
        return buildList {
            price.takeIf(String::isNotBlank)?.let { add("现价 $it") }
            changePercent.takeIf(String::isNotBlank)?.let { add("涨跌幅 $it") }
            open.takeIf(String::isNotBlank)?.let { add("今开 $it") }
            high.takeIf(String::isNotBlank)?.let { add("最高 $it") }
            low.takeIf(String::isNotBlank)?.let { add("最低 $it") }
            volume.takeIf(String::isNotBlank)?.let { add("成交量 $it$volumeUnit") }
            amount.takeIf(String::isNotBlank)?.let { add("成交额 $it$amountUnit") }
            turnoverRate.takeIf(String::isNotBlank)?.let { add("换手率 $it%") }
            priceEarningsRatio.takeIf(String::isNotBlank)?.let { add("市盈率 $it") }
            amplitude.takeIf(String::isNotBlank)?.let { add("振幅 $it%") }
            updatedAt.takeIf(String::isNotBlank)?.let { add(it) }
        }.joinToString("；").ifBlank { "已识别会话行情" }
    }

    private data class SecurityIdentity(
        val providerSymbol: String,
        val name: String,
        val symbol: String,
        val marketLabel: String,
    ) {
        val key: String
            get() = providerSymbol.ifBlank { "${name.lowercase()}|${symbol.lowercase()}" }
    }

    private data class DetectedSecurity(
        val index: Int,
        val identity: SecurityIdentity,
    )

    private data class SummaryMetrics(
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

    private data class KnownSecurity(
        val providerSymbol: String,
        val name: String,
        val aliases: List<String>,
        val marketLabel: String,
    )

    private fun KnownSecurity.toIdentity(): SecurityIdentity {
        return SecurityIdentity(
            providerSymbol = providerSymbol,
            name = name,
            symbol = providerSymbol.displaySymbol(),
            marketLabel = marketLabel,
        )
    }

    private const val MAX_TITLE_LENGTH = 60
    private const val DEFAULT_TITLE = "当前会话"
    private const val TABLE_ARTIFACT_TITLE_SUFFIX = " · 产物表格"
    private const val COMPARISON_TITLE_SUFFIX = " · 标的对比"
    private const val MARKET_AFFIX_SCAN_LENGTH = 5
    private const val METRIC_PREFIX_SCAN_LENGTH = 48
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val taggedSecurityRegex = Regex(
        "\\[行情标的:((?:(?:sh|sz|bj)\\d{6}|hk\\d{5}))\\|([^]]+)]",
        RegexOption.IGNORE_CASE,
    )
    private val providerSymbolRegex = Regex(
        "(?:^|[^\\d])((?:sh|sz|bj)\\s*\\d{6}|hk\\s*\\d{1,5})(?!\\d)",
        RegexOption.IGNORE_CASE,
    )
    private val suffixSymbolRegex = Regex(
        "(?:^|[^\\d])(\\d{6}\\s*[.]?(?:sh|sz|bj)|\\d{1,5}\\s*[.]?hk)(?!\\d)",
        RegexOption.IGNORE_CASE,
    )
    private val plainSymbolRegex = Regex("\\d{6}")
    private val marketPrefixBeforeCodeRegex = Regex(
        "(?:sh|sz|bj|hk)\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val marketSuffixAfterCodeRegex = Regex(
        "^\\s*[.]?(?:sh|sz|bj|hk)(?![A-Za-z])",
        RegexOption.IGNORE_CASE,
    )
    private val metricValuePrefixRegex = Regex(
        pattern = "(?:成交量|交易量|成交额|总市值|流通市值|市值|总股本|流通股本|" +
            "现价|价格|股价|昨收|今开|开盘价|收盘价|最高价|最低价|最高|最低|" +
            "涨跌额|涨跌幅|换手率|市盈率|振幅|金额|数量|成交笔数|营业收入|营收|" +
            "净利润|利润|现金流|总资产|总负债|时间|日期)" +
            "\\s*(?:[（(]\\s*(?:手|股|万股|亿股|元|万元|亿元|港元)\\s*[）)])?" +
            "\\s*(?:(?:大约为|约为|大约|约|为|是|达到|达|等于)|[:：=,，])*\\s*$",
        option = RegexOption.IGNORE_CASE,
    )
    private val knownSecurities = listOf(
        KnownSecurity("sh600519", "贵州茅台", listOf("贵州茅台", "茅台"), "沪市"),
        KnownSecurity("sz300750", "宁德时代", listOf("宁德时代", "宁德"), "深市"),
        KnownSecurity("sh000300", "沪深300", listOf("沪深300", "HS300"), "沪市指数"),
        KnownSecurity("sh000905", "中证500", listOf("中证500"), "沪市指数"),
        KnownSecurity("sh000001", "上证指数", listOf("上证指数", "上证综指", "大盘"), "沪市指数"),
        KnownSecurity("sz399001", "深证成指", listOf("深证成指", "深成指"), "深市指数"),
        KnownSecurity("sz399006", "创业板指", listOf("创业板指", "创业板指数"), "深市指数"),
        KnownSecurity("hk00700", "腾讯控股", listOf("腾讯控股"), "港股"),
        KnownSecurity("sz000858", "五粮液", listOf("五粮液"), "深市"),
        KnownSecurity("sz002594", "比亚迪", listOf("比亚迪"), "深市"),
        KnownSecurity("sh601318", "中国平安", listOf("中国平安"), "沪市"),
        KnownSecurity("sh600036", "招商银行", listOf("招商银行", "招行"), "沪市"),
        KnownSecurity("sh601398", "工商银行", listOf("工商银行", "工行"), "沪市"),
        KnownSecurity("sz000333", "美的集团", listOf("美的集团"), "深市"),
        KnownSecurity("sh601012", "隆基绿能", listOf("隆基绿能", "隆基股份"), "沪市"),
        KnownSecurity("sh688981", "中芯国际", listOf("中芯国际"), "沪市"),
        KnownSecurity("hk09988", "阿里巴巴-SW", listOf("阿里巴巴-SW", "阿里巴巴"), "港股"),
        KnownSecurity("hk03690", "美团-W", listOf("美团-W", "美团"), "港股"),
    )
}
