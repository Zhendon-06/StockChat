package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ChatHistoryItem

internal enum class SecuritiesIntent {
    QUOTE,
    TREND,
    COMPARE,
    ANALYSIS,
}

internal data class SecurityTarget(
    val providerSymbol: String,
    val displayName: String = "",
)

internal data class SecuritiesQueryPlan(
    val intent: SecuritiesIntent,
    val targets: List<SecurityTarget>,
    val unresolvedTerms: List<String>,
    val needsTrend: Boolean,
    val needsIntraday: Boolean,
    val needsAi: Boolean,
)

internal object SecuritiesQueryRouter {
    fun route(
        question: String,
        history: List<ChatHistoryItem> = emptyList(),
    ): SecuritiesQueryPlan? {
        val normalizedQuestion = question.trim()
        if (normalizedQuestion.isEmpty() || isGeneralKnowledgeQuestion(normalizedQuestion)) {
            return null
        }
        val compactQuestion = normalizedQuestion.replace(Regex("\\s+"), "")

        val targets = linkedMapOf<String, SecurityTarget>()
        knownSecurities.forEach { security ->
            if (security.aliases.any { alias -> compactQuestion.contains(alias, ignoreCase = true) }) {
                targets[security.providerSymbol] = SecurityTarget(
                    providerSymbol = security.providerSymbol,
                    displayName = security.displayName,
                )
            }
        }
        providerSymbolRegex.findAll(normalizedQuestion).forEach { match ->
            val market = match.groupValues[1].lowercase()
            val code = match.groupValues[2]
            targets[market + code] = SecurityTarget(market + code)
        }
        suffixSymbolRegex.findAll(normalizedQuestion).forEach { match ->
            val code = match.groupValues[1]
            val market = match.groupValues[2].lowercase()
            targets[market + code] = SecurityTarget(market + code)
        }
        plainCodeRegex.findAll(normalizedQuestion).forEach { match ->
            val hasDigitBefore = match.range.first > 0 &&
                normalizedQuestion[match.range.first - 1].isDigit()
            val hasDigitAfter = match.range.last < normalizedQuestion.lastIndex &&
                normalizedQuestion[match.range.last + 1].isDigit()
            if (hasDigitBefore || hasDigitAfter) {
                return@forEach
            }
            val code = match.value
            if (targets.values.none { it.providerSymbol.endsWith(code) }) {
                val providerSymbol = defaultProviderSymbol(code)
                targets[providerSymbol] = SecurityTarget(providerSymbol)
            }
        }

        if (targets.isEmpty() && containsContextPronoun(normalizedQuestion)) {
            latestHistoryTarget(history)?.let { target ->
                targets[target.providerSymbol] = target
            }
        }

        val hasMarketKeyword = marketKeywords.any(normalizedQuestion::contains)
        val hasTarget = targets.isNotEmpty()
        if (!hasTarget && !hasMarketKeyword) {
            return null
        }

        val intent = detectIntent(normalizedQuestion, targets.size)
        val unresolvedTerms = if (hasTarget) {
            emptyList()
        } else {
            extractSearchTerms(normalizedQuestion, intent)
        }
        if (!hasTarget && unresolvedTerms.isEmpty()) {
            return null
        }
        val asksForReasoning = analysisKeywords.any(normalizedQuestion::contains) ||
            reasoningKeywords.any(normalizedQuestion::contains)
        return SecuritiesQueryPlan(
            intent = intent,
            targets = targets.values.take(MAX_TARGETS),
            unresolvedTerms = unresolvedTerms.take(MAX_TARGETS),
            needsTrend = intent == SecuritiesIntent.TREND ||
                intent == SecuritiesIntent.ANALYSIS ||
                trendKeywords.any(normalizedQuestion::contains),
            needsIntraday = intradayKeywords.any(normalizedQuestion::contains),
            needsAi = intent == SecuritiesIntent.ANALYSIS ||
                (intent == SecuritiesIntent.COMPARE && asksForReasoning),
        )
    }

    private fun detectIntent(question: String, targetCount: Int): SecuritiesIntent {
        if (compareKeywords.any(question::contains) || targetCount > 1) {
            return SecuritiesIntent.COMPARE
        }
        if (analysisKeywords.any(question::contains) || reasoningKeywords.any(question::contains)) {
            return SecuritiesIntent.ANALYSIS
        }
        if (trendKeywords.any(question::contains)) {
            return SecuritiesIntent.TREND
        }
        return SecuritiesIntent.QUOTE
    }

    private fun extractSearchTerms(
        question: String,
        intent: SecuritiesIntent,
    ): List<String> {
        val segments = if (intent == SecuritiesIntent.COMPARE) {
            question.split(compareSeparatorRegex)
        } else {
            listOf(question)
        }
        return segments.mapNotNull { segment ->
            var candidate = segment.trim()
            searchFillers.forEach { filler ->
                candidate = candidate.replace(filler, "", ignoreCase = true)
            }
            candidate = candidate.replace(punctuationRegex, "").trim()
            candidate.takeIf { it.length in 2..24 }
        }.distinct()
    }

    private fun latestHistoryTarget(history: List<ChatHistoryItem>): SecurityTarget? {
        history.asReversed().forEach { item ->
            val match = historyTargetRegex.find(item.content) ?: return@forEach
            return SecurityTarget(
                providerSymbol = match.groupValues[1].lowercase(),
                displayName = match.groupValues[2].trim(),
            )
        }
        return null
    }

    private fun containsContextPronoun(question: String): Boolean {
        return contextPronouns.any(question::contains)
    }

    private fun isGeneralKnowledgeQuestion(question: String): Boolean {
        val compactQuestion = question.replace(Regex("\\s+"), "")
        if (providerSymbolRegex.containsMatchIn(question) || suffixSymbolRegex.containsMatchIn(question) ||
            plainCodeRegex.containsMatchIn(question) ||
            knownSecurities.any { security ->
                security.aliases.any { alias -> compactQuestion.contains(alias, ignoreCase = true) }
            }
        ) {
            return false
        }
        return knowledgePrefixes.any(question::contains) &&
            knowledgeTopics.any(question::contains)
    }

    private fun defaultProviderSymbol(code: String): String {
        return when (code.firstOrNull()) {
            '4', '8' -> "bj$code"
            '5', '6', '9' -> "sh$code"
            else -> "sz$code"
        }
    }

    private data class KnownSecurity(
        val providerSymbol: String,
        val displayName: String,
        val aliases: List<String>,
    )

    private const val MAX_TARGETS = 4
    private val knownSecurities = listOf(
        KnownSecurity("sh600519", "贵州茅台", listOf("贵州茅台", "茅台")),
        KnownSecurity("sz300750", "宁德时代", listOf("宁德时代", "宁德")),
        KnownSecurity("sh000300", "沪深300", listOf("沪深300", "HS300")),
        KnownSecurity("sh000905", "中证500", listOf("中证500")),
        KnownSecurity("sh000001", "上证指数", listOf("上证指数", "上证综指", "大盘")),
        KnownSecurity("sz399001", "深证成指", listOf("深证成指", "深成指")),
        KnownSecurity("sz399006", "创业板指", listOf("创业板指", "创业板指数")),
    )
    private val providerSymbolRegex = Regex("(?i)(sh|sz|bj)\\s*(\\d{6})")
    private val suffixSymbolRegex = Regex("(?i)(\\d{6})\\s*[.]?(sh|sz|bj)")
    private val plainCodeRegex = Regex("\\d{6}")
    private val historyTargetRegex = Regex(
        "\\[行情标的:((?:sh|sz|bj)\\d{6})\\|([^]]+)]",
        RegexOption.IGNORE_CASE,
    )
    private val punctuationRegex = Regex("[\\s，,。！？?：:；;（）()【】\\[\\]《》]")
    private val compareSeparatorRegex = Regex(
        "和|与|跟|、|vs|pk|对比|比较|相比",
        RegexOption.IGNORE_CASE,
    )
    private val contextPronouns = listOf("它", "这只", "该股", "这个股票", "这个指数", "这个标的")
    private val compareKeywords = listOf("对比", "比较", "相比", "谁更", "哪个更", "vs", "VS", "pk", "PK")
    private val analysisKeywords = listOf(
        "分析", "解读", "怎么看", "值得买", "能买吗", "风险", "估值", "预测", "会涨", "会跌",
    )
    private val reasoningKeywords = listOf("为什么", "原因", "哪个好", "谁更好", "建议")
    private val trendKeywords = listOf(
        "走势", "趋势", "分时", "K线", "k线", "日线", "周线", "月线", "近几日", "最近表现",
    )
    private val intradayKeywords = listOf("分时", "盘中", "今天走势", "今日走势", "今天表现", "今日表现")
    private val quoteKeywords = listOf(
        "行情", "现价", "价格", "股价", "多少钱", "涨跌", "涨幅", "跌幅", "开盘", "昨收", "最高",
        "最低", "成交量", "成交额", "换手", "市盈率", "PE", "pe", "实时",
    )
    private val marketKeywords = quoteKeywords + trendKeywords + analysisKeywords + reasoningKeywords +
        compareKeywords + listOf("股票", "证券", "指数", "大盘")
    private val knowledgePrefixes = listOf(
        "什么是", "是什么", "是什么意思", "怎么计算", "怎么算", "如何理解", "怎么看", "定义",
    )
    private val knowledgeTopics = listOf("市盈率", "PE", "pe", "K线", "k线", "换手率", "成交量", "指数")
    private val searchFillers = listOf(
        "请帮我查一下", "帮我查一下", "请帮我看看", "帮我看看", "请问", "查一下", "看一下", "看看",
        "现在多少钱", "当前多少钱", "今天多少钱", "最近表现", "今天表现", "今日表现", "今天走势",
        "今日走势", "实时行情", "当前行情", "最新行情", "行情", "现价", "价格", "股价", "涨跌幅",
        "涨跌", "走势", "趋势", "分时", "K线", "k线", "日线", "周线", "月线", "股票", "证券",
        "指数", "分析一下", "分析", "解读一下", "解读", "怎么样", "如何", "为什么", "原因", "值得买",
        "能买吗", "风险", "预测", "对比", "比较", "相比", "谁更好", "哪个好", "的",
    ).sortedByDescending(String::length)
}
