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
import com.guet.liang.stockchat.model.TencentMarketSnapshot

internal fun detectSecurities(text: String): List<SecurityIdentity> {
    if (text.isBlank()) return emptyList()
    val matches = taggedSecurityMatches(text) + knownSecurityMatches(text) +
        explicitSecurityMatches(text, providerSymbolRegex) + explicitSecurityMatches(text, suffixSymbolRegex) + bareSecurityMatches(text)
    val detectedByKey = linkedMapOf<String, SecurityIdentity>()
    matches.sortedBy(DetectedSecurity::index).forEach { match ->
        val existing = detectedByKey[match.identity.key]
        detectedByKey[match.identity.key] = existing?.copy(
            name = existing.name.ifBlank { match.identity.name },
            marketLabel = existing.marketLabel.ifBlank { match.identity.marketLabel },
        ) ?: match.identity
    }
    return detectedByKey.values.toList()
}

private fun taggedSecurityMatches(text: String): List<DetectedSecurity> = taggedSecurityRegex.findAll(text).mapNotNull { match ->
    normalizeProviderSymbol(match.groupValues[1])?.let { providerSymbol ->
        DetectedSecurity(match.range.first, identityForProvider(providerSymbol, match.groupValues[2].trim()))
    }
}.toList()

private fun knownSecurityMatches(text: String): List<DetectedSecurity> = knownSecurities.flatMap { security ->
    security.aliases.mapNotNull { alias ->
        text.indexOf(alias, ignoreCase = true).takeIf { it >= 0 }?.let { DetectedSecurity(it, security.toIdentity()) }
    }
}

private fun explicitSecurityMatches(text: String, regex: Regex): List<DetectedSecurity> = regex.findAll(text).mapNotNull { match ->
    match.groups[1]?.value?.let(::normalizeProviderSymbol)?.let { DetectedSecurity(match.range.first, identityForProvider(it)) }
}.toList()

private fun bareSecurityMatches(text: String): List<DetectedSecurity> = plainSymbolRegex.findAll(text).mapNotNull { match ->
    if (excludedBareSecurity(text, match)) null else {
        providerSymbolForBareCode(match.value)?.let { DetectedSecurity(match.range.first, identityForProvider(it)) }
    }
}.toList()

private fun excludedBareSecurity(text: String, match: MatchResult): Boolean {
    val hasDigitBefore = match.range.first > 0 && text[match.range.first - 1].isDigit()
    val hasDigitAfter = match.range.last < text.lastIndex && text[match.range.last + 1].isDigit()
    val explicitOrAttached = hasDigitBefore || hasDigitAfter || isPartOfExplicitMarketSymbol(text, match)
    return explicitOrAttached || isMetricValue(text, match) || isDecimalValue(text, match)
}

internal fun providerSymbolForBareCode(code: String): String? {
    return knownSecurities.firstOrNull { security ->
        security.providerSymbol.displaySymbol() == code
    }?.providerSymbol ?: normalizeProviderSymbol(code)
}

internal fun isPartOfExplicitMarketSymbol(
    text: String,
    match: MatchResult,
): Boolean {
    val prefix = text.substring(0, match.range.first).takeLast(MARKET_AFFIX_SCAN_LENGTH)
    val suffix = text.substring(match.range.last + 1).take(MARKET_AFFIX_SCAN_LENGTH)
    return marketPrefixBeforeCodeRegex.containsMatchIn(prefix) ||
        marketSuffixAfterCodeRegex.containsMatchIn(suffix)
}

internal fun isMetricValue(
    text: String,
    match: MatchResult,
): Boolean {
    val prefix = text.substring(0, match.range.first).takeLast(METRIC_PREFIX_SCAN_LENGTH)
    return metricValuePrefixRegex.containsMatchIn(prefix)
}

internal fun isDecimalValue(
    text: String,
    match: MatchResult,
): Boolean {
    val decimalPointIndex = match.range.last + 1
    val fractionIndex = decimalPointIndex + 1
    return decimalPointIndex < text.length && text[decimalPointIndex] == '.' &&
        fractionIndex < text.length && text[fractionIndex].isDigit()
}

internal fun identityForQuote(quote: StockQuote): SecurityIdentity {
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

internal fun identityForProvider(
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

internal fun SecurityIdentity.toComparisonRow(): ConversationStockComparisonRow {
    return ConversationStockComparisonRow(
        providerSymbol = providerSymbol,
        name = name,
        symbol = symbol,
        marketLabel = marketLabel,
    )
}

internal fun String.normalizedDisplaySymbol(): String {
    return trim().uppercase().let { value ->
        normalizeProviderSymbol(value)?.displaySymbol() ?: value
    }
}

internal fun String.displaySymbol(): String {
    return when {
        startsWith("hk") -> drop(2).padStart(HK_SYMBOL_WIDTH, '0')
        startsWith("sh") || startsWith("sz") || startsWith("bj") -> drop(2)
        else -> this
    }
}

internal fun marketPrefix(marketLabel: String): String? {
    return when {
        marketLabel.startsWith("沪市") -> "sh"
        marketLabel.startsWith("深市") -> "sz"
        marketLabel.startsWith("北交所") -> "bj"
        marketLabel.startsWith("港股") -> "hk"
        else -> null
    }
}

internal fun marketLabelForProvider(providerSymbol: String): String {
    return when {
        providerSymbol.startsWith("sh") -> "沪市"
        providerSymbol.startsWith("sz") -> "深市"
        providerSymbol.startsWith("bj") -> "北交所"
        providerSymbol.startsWith("hk") -> "港股"
        else -> ""
    }
}

/** Parsed comparison data shared by the generator helpers. */
internal data class SecurityIdentity(
    val providerSymbol: String,
    val name: String,
    val symbol: String,
    val marketLabel: String,
) {
    val key: String
        get() = providerSymbol.ifBlank { "${name.lowercase()}|${symbol.lowercase()}" }
}

/** Parsed comparison data shared by the generator helpers. */
internal data class DetectedSecurity(
    val index: Int,
    val identity: SecurityIdentity,
)

/** Parsed comparison data shared by the generator helpers. */
internal data class KnownSecurity(
    val providerSymbol: String,
    val name: String,
    val aliases: List<String>,
    val marketLabel: String,
)

internal fun KnownSecurity.toIdentity(): SecurityIdentity {
    return SecurityIdentity(
        providerSymbol = providerSymbol,
        name = name,
        symbol = providerSymbol.displaySymbol(),
        marketLabel = marketLabel,
    )
}

internal const val MARKET_AFFIX_SCAN_LENGTH = 5
internal const val METRIC_PREFIX_SCAN_LENGTH = 48
internal val taggedSecurityRegex = Regex(
    "\\[行情标的:((?:(?:sh|sz|bj)\\d{6}|hk\\d{5}))\\|([^]]+)]",
    RegexOption.IGNORE_CASE,
)
internal val providerSymbolRegex = Regex(
    "(?:^|[^\\d])((?:sh|sz|bj)\\s*\\d{6}|hk\\s*\\d{1,5})(?!\\d)",
    RegexOption.IGNORE_CASE,
)
internal val suffixSymbolRegex = Regex(
    "(?:^|[^\\d])(\\d{6}\\s*[.]?(?:sh|sz|bj)|\\d{1,5}\\s*[.]?hk)(?!\\d)",
    RegexOption.IGNORE_CASE,
)
internal val plainSymbolRegex = Regex("\\d{6}")
internal val marketPrefixBeforeCodeRegex = Regex(
    "(?:sh|sz|bj|hk)\\s*$",
    RegexOption.IGNORE_CASE,
)
internal val marketSuffixAfterCodeRegex = Regex(
    "^\\s*[.]?(?:sh|sz|bj|hk)(?![A-Za-z])",
    RegexOption.IGNORE_CASE,
)
internal val metricValuePrefixRegex = Regex(
    pattern = "(?:成交量|交易量|成交额|总市值|流通市值|市值|总股本|流通股本|" +
        "现价|价格|股价|昨收|今开|开盘价|收盘价|最高价|最低价|最高|最低|" +
        "涨跌额|涨跌幅|换手率|市盈率|振幅|金额|数量|成交笔数|营业收入|营收|" +
        "净利润|利润|现金流|总资产|总负债|时间|日期)" +
        "\\s*(?:[（(]\\s*(?:手|股|万股|亿股|元|万元|亿元|港元)\\s*[）)])?" +
        "\\s*(?:(?:大约为|约为|大约|约|为|是|达到|达|等于)|[:：=,，])*\\s*$",
    option = RegexOption.IGNORE_CASE,
)
internal val knownSecurities = listOf(
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

private const val HK_SYMBOL_WIDTH = 5
