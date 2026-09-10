package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatAnswer
import com.guet.liang.stockchat.model.TencentMarketSnapshot

// 并行回答合并：聊天分支负责正文与流式输出，标的分支负责行情卡片。
// 卡片一旦就绪就随流式片段一起展示；最终结果等两条分支都结束。

/** Outcome of the stock branch. It degrades to notices instead of failing the whole answer. */
internal data class MarketBranchOutcome(
    val plan: SecuritiesQueryPlan? = null,
    val snapshots: List<TencentMarketSnapshot> = emptyList(),
    val notices: List<String> = emptyList(),
) {
    val quoteBlocks: List<AnswerBlock> get() = snapshots.map { AnswerBlock.MarketQuote(it.quote) }

    companion object {
        /** The message mentioned no security, or images went straight to the vision model. */
        val NONE = MarketBranchOutcome()
    }
}

/**
 * Joins the context-carrying chat request with the independent stock request.
 * Streaming deltas are forwarded immediately, carrying quote cards as soon as the stock branch
 * has them; the final [ChatAnswer] waits for both branches.
 */
internal class ParallelAnswerJoin(
    private val callback: (ChatAnswer) -> Unit,
    private val onMergedSuccess: (List<AnswerBlock>) -> Unit = {},
) {
    private var chat: ChatAnswer? = null
    private var market: MarketBranchOutcome? = null
    private var latestMarkdown = ""
    private var completed = false

    fun onChat(answer: ChatAnswer) {
        if (completed) return
        if (answer is ChatAnswer.Streaming) {
            latestMarkdown = answer.markdown
            callback(ChatAnswer.Streaming(answer.markdown, market?.quoteBlocks.orEmpty()))
            return
        }
        if (chat == null) {
            chat = answer
            tryComplete()
        }
    }

    fun onMarket(outcome: MarketBranchOutcome) {
        if (completed || market != null) return
        market = outcome
        if (chat == null && latestMarkdown.isNotEmpty() && outcome.snapshots.isNotEmpty()) {
            // Text is still streaming: show the cards now instead of holding them until the end.
            callback(ChatAnswer.Streaming(latestMarkdown, outcome.quoteBlocks))
        }
        tryComplete()
    }

    private fun tryComplete() {
        val chatAnswer = chat ?: return
        val outcome = market ?: return
        completed = true
        val merged = merge(chatAnswer, outcome)
        if (merged is ChatAnswer.Success) onMergedSuccess(merged.blocks)
        callback(merged)
    }

    companion object {
        fun merge(chat: ChatAnswer, outcome: MarketBranchOutcome): ChatAnswer = when (chat) {
            is ChatAnswer.Streaming -> chat
            is ChatAnswer.Success -> ChatAnswer.Success(mergedAnswerBlocks(chat.blocks, outcome))
            is ChatAnswer.Failure -> {
                val plan = outcome.plan
                if (plan != null && outcome.snapshots.isNotEmpty()) {
                    ChatAnswer.Success(marketOnlyAnswerBlocks(plan, outcome.snapshots, outcome.notices))
                } else {
                    ChatAnswer.Failure((listOf(chat.message) + outcome.notices).joinToString("\n"))
                }
            }
        }
    }
}

/** Appends stock notices to the chat markdown and attaches one quote card per snapshot. */
internal fun mergedAnswerBlocks(chatBlocks: List<AnswerBlock>, outcome: MarketBranchOutcome): List<AnswerBlock> {
    val notices = outcome.notices.joinToString("\n")
    val blocks = if (notices.isBlank()) chatBlocks else {
        val markdownIndex = chatBlocks.indexOfFirst { it is AnswerBlock.Markdown }
        if (markdownIndex < 0) {
            chatBlocks + AnswerBlock.Markdown(notices, notices)
        } else {
            chatBlocks.mapIndexed { index, block ->
                if (index != markdownIndex || block !is AnswerBlock.Markdown) block else {
                    val text = listOf(block.source.trim(), notices).filter(String::isNotBlank).joinToString("\n\n")
                    AnswerBlock.Markdown(text, text)
                }
            }
        }
    }
    return blocks + outcome.quoteBlocks
}

/** Shown when the chat model failed but Tencent still returned verifiable quotes. */
internal fun marketOnlyAnswerBlocks(
    plan: SecuritiesQueryPlan,
    snapshots: List<TencentMarketSnapshot>,
    notices: List<String>,
): List<AnswerBlock> {
    val names = snapshots.joinToString("、") { snapshot -> "${snapshot.quote.name}（${snapshot.quote.symbol}）" }
    val headline = when (plan.intent) {
        SecuritiesIntent.QUOTE -> "已获取 $names 的最新行情快照。"
        SecuritiesIntent.TREND -> "已获取 $names 的最新行情与走势数据。"
        SecuritiesIntent.COMPARE -> "已获取 $names 的同期行情，可通过卡片对比价格与涨跌幅。"
        SecuritiesIntent.ANALYSIS -> "已获取 $names 的最新行情。"
    }
    val markdown = listOf(
        "StockChat Demo 信息。$headline",
        "AI 深度解读当前不可用，先展示可核验的行情数据。",
        notices.joinToString("\n"),
        "数据来源：腾讯证券公开行情接口；行情时间以卡片标注为准。仅供参考，不构成投资建议。",
    ).filter(String::isNotBlank).joinToString("\n\n")
    return buildList {
        add(AnswerBlock.Markdown(markdown, markdown))
        snapshots.forEach { snapshot -> add(AnswerBlock.MarketQuote(snapshot.quote)) }
    }
}

/** Prompt fragment that lets the precise mode cite Tencent quotes; the model may only quote these fields. */
internal fun marketContextPrompt(snapshots: List<TencentMarketSnapshot>): String {
    val lines = snapshots.joinToString("\n") { snapshot ->
        val quote = snapshot.quote
        val trend = quote.trendPoints.takeLast(MARKET_CONTEXT_TREND_POINTS).joinToString(",")
        "- ${quote.name}（${quote.symbol}，${snapshot.providerSymbol}）：" +
            "现价 ${quote.price}，涨跌 ${quote.change}（${quote.changePercent}），" +
            "昨收 ${snapshot.previousClose}，今开 ${snapshot.open}，最高 ${snapshot.high}，" +
            "最低 ${snapshot.low}，成交量 ${snapshot.volume} ${snapshot.volumeUnit}，" +
            "成交额 ${snapshot.amount} ${snapshot.amountUnit}，" +
            "换手率 ${snapshot.turnoverRate}%，市盈率 ${snapshot.priceEarningsRatio}，" +
            "振幅 ${snapshot.amplitude}%，最近走势点（从旧到新）[$trend]，${quote.updatedAt}"
    }
    return "以下是本次请求刚获取的腾讯证券行情工具数据，实时数字只能引用这些字段：\n$lines"
}


private const val MARKET_CONTEXT_TREND_POINTS = 10
