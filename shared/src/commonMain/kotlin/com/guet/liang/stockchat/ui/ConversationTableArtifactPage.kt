package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.data.ChatHistoryDatabase
import com.guet.liang.stockchat.data.ConversationStockComparisonDataSource
import com.guet.liang.stockchat.data.ConversationStockComparisonGenerator
import com.guet.liang.stockchat.model.ConversationStockComparisonRow
import com.guet.liang.stockchat.model.ConversationStockComparisonSnapshot
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

private enum class ComparisonRefreshPhase {
    REFRESHING,
    CURRENT,
    PARTIAL,
    FAILED,
    SESSION_ONLY,
}

private data class ComparisonContentUi(
    val snapshot: ConversationStockComparisonSnapshot,
    val refreshPhase: ComparisonRefreshPhase,
    val refreshedCount: Int,
    val completedCount: Int,
    val refreshTargetCount: Int,
)

private sealed class ComparisonDetailUiState {
    data object Loading : ComparisonDetailUiState()
    data object NotFound : ComparisonDetailUiState()
    data class Empty(
        val title: String,
        val sourceMessageCount: Int,
    ) : ComparisonDetailUiState()
    data class Refreshing(val content: ComparisonContentUi) : ComparisonDetailUiState()
    data class Content(val content: ComparisonContentUi) : ComparisonDetailUiState()
    data class Error(val message: String) : ComparisonDetailUiState()
}

@Page(CONVERSATION_TABLE_ARTIFACT_PAGE_NAME, supportInLocal = true)
internal class ConversationTableArtifactPage : BasePager() {
    private var uiState by observable<ComparisonDetailUiState>(ComparisonDetailUiState.Loading)
    private var artifactIdText = ""
    private var refreshToken = 0
    private var baseSnapshot: ConversationStockComparisonSnapshot? = null
    private lateinit var comparisonDataSource: ConversationStockComparisonDataSource

    override fun created() {
        super.created()
        artifactIdText = pageData.params
            .optString(CONVERSATION_TABLE_ARTIFACT_ID_PARAM)
            .trim()
        comparisonDataSource = ConversationStockComparisonDataSource(
            acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
        )
        loadComparison()
    }

    override fun pageWillDestroy() {
        refreshToken += 1
        super.pageWillDestroy()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(StockChatTheme.background)
            }
            ctx.PageHeader(this)
            View {
                attr {
                    absolutePosition(
                        top = pagerData.statusBarHeight + HEADER_HEIGHT,
                        left = 0f,
                        right = 0f,
                        bottom = pagerData.safeAreaInsets.bottom,
                    )
                }
                vif({ ctx.uiState is ComparisonDetailUiState.Loading }) {
                    ctx.LoadingState(this)
                }
                vif({ ctx.uiState is ComparisonDetailUiState.NotFound }) {
                    ctx.NotFoundState(this)
                }
                vif({ ctx.uiState is ComparisonDetailUiState.Empty }) {
                    ctx.EmptyState(this, ctx.uiState as ComparisonDetailUiState.Empty)
                }
                vif({ ctx.uiState is ComparisonDetailUiState.Error }) {
                    ctx.ErrorState(this, (ctx.uiState as ComparisonDetailUiState.Error).message)
                }
                vif({ ctx.uiState is ComparisonDetailUiState.Refreshing }) {
                    ctx.ComparisonContent(
                        this,
                        (ctx.uiState as ComparisonDetailUiState.Refreshing).content,
                    )
                }
                vif({ ctx.uiState is ComparisonDetailUiState.Content }) {
                    ctx.ComparisonContent(
                        this,
                        (ctx.uiState as ComparisonDetailUiState.Content).content,
                    )
                }
            }
        }
    }

    private fun PageHeader(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            View {
                attr {
                    height(pagerData.statusBarHeight + HEADER_HEIGHT)
                    padding(
                        top = pagerData.statusBarHeight + 12f,
                        left = 18f,
                        right = 18f,
                    )
                    backgroundColor(StockChatTheme.background)
                    flexDirectionRow()
                    alignItemsCenter()
                }
                View {
                    attr {
                        size(44f, 44f)
                        borderRadius(22f)
                        backgroundColor(StockChatTheme.surface)
                        border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                        allCenter()
                    }
                    event {
                        click { ctx.closePage() }
                    }
                    Text {
                        attr {
                            text("‹")
                            fontSize(34f)
                            color(StockChatTheme.textPrimary)
                            marginBottom(3f)
                        }
                    }
                }
                View {
                    attr {
                        flex(1f)
                        marginLeft(14f)
                    }
                    Text {
                        attr {
                            text("会话表格对比")
                            fontSize(20f)
                            fontWeightBold()
                            color(StockChatTheme.textPrimary)
                            lines(1)
                        }
                    }
                    Text {
                        attr {
                            text("关键行情指标汇总")
                            fontSize(11f)
                            color(StockChatTheme.textTertiary)
                            marginTop(2f)
                            lines(1)
                        }
                    }
                }
            }
        }
    }

    private fun LoadingState(container: ViewContainer<*, *>) {
        with(container) {
            View {
                attr {
                    absolutePositionAllZero()
                    allCenter()
                }
                View {
                    attr {
                        size(48f, 48f)
                        borderRadius(16f)
                        backgroundColor(StockChatTheme.accentSoft)
                        allCenter()
                    }
                    Text {
                        attr {
                            text("…")
                            fontSize(24f)
                            color(StockChatTheme.accent)
                            marginBottom(8f)
                        }
                    }
                }
                Text {
                    attr {
                        text("正在读取表格对比")
                        fontSize(14f)
                        color(StockChatTheme.textSecondary)
                        marginTop(14f)
                    }
                }
            }
        }
    }

    private fun NotFoundState(container: ViewContainer<*, *>) {
        with(container) {
            View {
                attr {
                    absolutePositionAllZero()
                    allCenter()
                    padding(left = 36f, right = 36f)
                }
                Text {
                    attr {
                        text("未找到该会话对比")
                        fontSize(20f)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text("对应会话可能已删除，请返回对比列表重新选择。")
                        fontSize(14f)
                        lineHeight(21f)
                        textAlignCenter()
                        color(StockChatTheme.textSecondary)
                        marginTop(8f)
                    }
                }
            }
        }
    }

    private fun EmptyState(
        container: ViewContainer<*, *>,
        state: ComparisonDetailUiState.Empty,
    ) {
        with(container) {
            View {
                attr {
                    absolutePositionAllZero()
                    allCenter()
                    padding(left = 36f, right = 36f)
                }
                View {
                    attr {
                        size(64f, 64f)
                        borderRadius(22f)
                        backgroundColor(StockChatTheme.accentSoft)
                        allCenter()
                    }
                    Text {
                        attr {
                            text("表")
                            fontSize(24f)
                            fontWeightBold()
                            color(StockChatTheme.accent)
                        }
                    }
                }
                Text {
                    attr {
                        text("该会话还没有可对比标的")
                        fontSize(20f)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        marginTop(18f)
                    }
                }
                Text {
                    attr {
                        text("在聊天中提及股票或指数名称、代码，或让 AI 生成相关行情后再试。")
                        fontSize(14f)
                        lineHeight(21f)
                        textAlignCenter()
                        color(StockChatTheme.textSecondary)
                        marginTop(8f)
                    }
                }
                Text {
                    attr {
                        text("${state.title} · 来源消息 ${state.sourceMessageCount} 条")
                        fontSize(11f)
                        color(StockChatTheme.textTertiary)
                        marginTop(12f)
                        lines(2)
                        textAlignCenter()
                    }
                }
            }
        }
    }

    private fun ErrorState(container: ViewContainer<*, *>, message: String) {
        val ctx = this
        with(container) {
            View {
                attr {
                    absolutePositionAllZero()
                    allCenter()
                    padding(left = 36f, right = 36f)
                }
                Text {
                    attr {
                        text("表格对比读取失败")
                        fontSize(20f)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text(message)
                        fontSize(14f)
                        lineHeight(21f)
                        textAlignCenter()
                        color(StockChatTheme.textSecondary)
                        marginTop(8f)
                    }
                }
                View {
                    attr {
                        height(42f)
                        borderRadius(21f)
                        backgroundColor(StockChatTheme.accent)
                        padding(left = 22f, right = 22f)
                        allCenter()
                        marginTop(20f)
                    }
                    event {
                        click { ctx.loadComparison() }
                    }
                    Text {
                        attr {
                            text("重新汇总")
                            fontSize(14f)
                            fontWeightBold()
                            color(Color.WHITE)
                        }
                    }
                }
            }
        }
    }

    private fun ComparisonContent(
        container: ViewContainer<*, *>,
        content: ComparisonContentUi,
    ) {
        val ctx = this
        val contentHeight = (
            pagerData.pageViewHeight -
                pagerData.statusBarHeight -
                HEADER_HEIGHT -
                pagerData.safeAreaInsets.bottom
            ).coerceAtLeast(0f)
        val tableHeight = (contentHeight - NON_TABLE_CONTENT_HEIGHT).coerceAtLeast(MIN_TABLE_HEIGHT)
        with(container) {
            Scroller {
                attr {
                    absolutePositionAllZero()
                    padding(top = 14f, left = 16f, right = 16f, bottom = 12f)
                    showScrollerIndicator(false)
                    bouncesEnable(true)
                }
                View {
                    attr {
                        width((pagerData.pageViewWidth - 32f).coerceAtLeast(1f))
                        alignSelfCenter()
                    }
                    ctx.ComparisonSummary(this, content)
                    ctx.RefreshStatus(this, content)
                    View {
                        attr {
                            height(TABLE_SECTION_HEADER_HEIGHT)
                            flexDirectionRow()
                            alignItemsCenter()
                            marginTop(10f)
                        }
                        View {
                            attr {
                                flex(1f)
                            }
                            Text {
                                attr {
                                    text("关键交易指标")
                                    fontSize(16f)
                                    fontWeightBold()
                                    color(StockChatTheme.textPrimary)
                                }
                            }
                            Text {
                                attr {
                                    text("点击任意一行进入行情详情")
                                    fontSize(10f)
                                    color(StockChatTheme.textTertiary)
                                    marginTop(2f)
                                }
                            }
                        }
                        Text {
                            attr {
                                text("左右滑动查看全部列")
                                fontSize(11f)
                                color(StockChatTheme.textTertiary)
                            }
                        }
                    }
                    View {
                        attr {
                            height(tableHeight)
                            alignSelfStretch()
                            borderRadius(14f)
                            border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                            backgroundColor(StockChatTheme.surface)
                            overflow(true)
                        }
                        ConversationStockComparisonTable(
                            rows = content.snapshot.rows,
                            viewportHeight = tableHeight,
                            onRowClick = ctx::openStockDetail,
                        )
                    }
                    View {
                        attr {
                            height(RISK_NOTICE_HEIGHT)
                            borderRadius(14f)
                            backgroundColor(StockChatTheme.warningSoft)
                            padding(left = 14f, right = 14f)
                            justifyContentCenter()
                            marginTop(12f)
                        }
                        Text {
                            attr {
                                text(content.snapshot.disclaimer)
                                fontSize(12f)
                                lineHeight(18f)
                                color(StockChatTheme.warning)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun ComparisonSummary(
        container: ViewContainer<*, *>,
        content: ComparisonContentUi,
    ) {
        val ctx = this
        val snapshot = content.snapshot
        val userMentionedCount = snapshot.rows.count(ConversationStockComparisonRow::mentionedByUser)
        val aiGeneratedCount = snapshot.rows.count(ConversationStockComparisonRow::generatedByAi)
        with(container) {
            View {
                attr {
                    height(SUMMARY_HEIGHT)
                    borderRadius(20f)
                    backgroundColor(StockChatTheme.surface)
                    border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                    padding(top = 14f, left = 16f, right = 16f, bottom = 14f)
                }
                Text {
                    attr {
                        text(snapshot.title)
                        fontSize(18f)
                        fontWeightBold()
                        lineHeight(23f)
                        lines(2)
                        color(StockChatTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text("从 ${snapshot.sourceMessageCount} 条消息中去重汇总，不再复述整段问答")
                        fontSize(11f)
                        color(StockChatTheme.textSecondary)
                        marginTop(5f)
                        lines(1)
                    }
                }
                View {
                    attr {
                        flex(1f)
                        flexDirectionRow()
                        alignItemsFlexEnd()
                        marginTop(10f)
                    }
                    ctx.ComparisonSummaryMetric(
                        this,
                        "${snapshot.rows.size}",
                        "全部标的",
                        isFirst = true,
                    )
                    ctx.ComparisonSummaryMetric(this, "$userMentionedCount", "用户提及")
                    ctx.ComparisonSummaryMetric(this, "$aiGeneratedCount", "AI 生成")
                }
            }
        }
    }

    private fun ComparisonSummaryMetric(
        container: ViewContainer<*, *>,
        value: String,
        label: String,
        isFirst: Boolean = false,
    ) {
        with(container) {
            View {
                attr {
                    flex(1f)
                    height(42f)
                    borderRadius(12f)
                    backgroundColor(if (isFirst) StockChatTheme.accentSoft else StockChatTheme.surfaceSoft)
                    border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                    marginRight(if (label == "AI 生成") 0f else 8f)
                    flexDirectionRow()
                    alignItemsCenter()
                    padding(left = 10f, right = 8f)
                }
                Text {
                    attr {
                        text(value)
                        fontSize(17f)
                        fontWeightBold()
                        color(if (isFirst) StockChatTheme.accent else StockChatTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text(label)
                        fontSize(10f)
                        color(StockChatTheme.textSecondary)
                        marginLeft(5f)
                        lines(1)
                    }
                }
            }
        }
    }

    private fun RefreshStatus(
        container: ViewContainer<*, *>,
        content: ComparisonContentUi,
    ) {
        val ctx = this
        val isWarning = content.refreshPhase == ComparisonRefreshPhase.PARTIAL ||
            content.refreshPhase == ComparisonRefreshPhase.FAILED
        val statusBackground = when {
            isWarning -> StockChatTheme.warningSoft
            content.refreshPhase == ComparisonRefreshPhase.SESSION_ONLY -> StockChatTheme.recessed
            else -> StockChatTheme.accentSoft
        }
        val statusColor = when {
            isWarning -> StockChatTheme.warning
            content.refreshPhase == ComparisonRefreshPhase.SESSION_ONLY -> StockChatTheme.textSecondary
            else -> StockChatTheme.accent
        }
        val statusText = when (content.refreshPhase) {
            ComparisonRefreshPhase.REFRESHING ->
                "正在同步最新行情 ${content.completedCount}/${content.refreshTargetCount}"
            ComparisonRefreshPhase.CURRENT ->
                "已同步 ${content.refreshedCount} 个标的的最新行情"
            ComparisonRefreshPhase.PARTIAL ->
                "已更新 ${content.refreshedCount}/${content.refreshTargetCount}，其余保留会话行情"
            ComparisonRefreshPhase.FAILED ->
                "实时行情暂不可用，当前展示会话中的最近数据"
            ComparisonRefreshPhase.SESSION_ONLY ->
                "已完成会话表格汇总，暂无可刷新的证券代码"
        }
        with(container) {
            View {
                attr {
                    height(REFRESH_STATUS_HEIGHT)
                    borderRadius(13f)
                    backgroundColor(statusBackground)
                    padding(left = 13f, right = 13f)
                    marginTop(10f)
                    flexDirectionRow()
                    alignItemsCenter()
                }
                if (content.refreshPhase != ComparisonRefreshPhase.REFRESHING &&
                    content.refreshPhase != ComparisonRefreshPhase.SESSION_ONLY
                ) {
                    event {
                        click { ctx.refreshMarketData() }
                    }
                }
                Text {
                    attr {
                        text(statusText)
                        fontSize(11f)
                        fontWeightMedium()
                        color(statusColor)
                        flex(1f)
                        lines(1)
                    }
                }
                if (content.refreshPhase != ComparisonRefreshPhase.REFRESHING &&
                    content.refreshPhase != ComparisonRefreshPhase.SESSION_ONLY
                ) {
                    Text {
                        attr {
                            text("重新刷新")
                            fontSize(11f)
                            fontWeightBold()
                            color(statusColor)
                            marginLeft(8f)
                        }
                    }
                }
            }
        }
    }

    private fun loadComparison() {
        val artifactId = artifactIdText.toLongOrNull()
        if (artifactId == null || artifactId <= 0L) {
            uiState = ComparisonDetailUiState.Error("对比标识无效，请返回列表重新选择。")
            return
        }
        refreshToken += 1
        uiState = ComparisonDetailUiState.Loading
        try {
            val artifact = ChatHistoryDatabase.artifactRepository().load(artifactId)
            if (artifact == null) {
                uiState = ComparisonDetailUiState.NotFound
                return
            }
            val messages = ChatHistoryDatabase.repository().loadMessages(artifact.sessionId)
            val snapshot = ConversationStockComparisonGenerator.generate(
                title = artifact.title,
                messages = messages,
            )
            baseSnapshot = snapshot
            if (snapshot.rows.isEmpty()) {
                uiState = ComparisonDetailUiState.Empty(
                    title = snapshot.title,
                    sourceMessageCount = snapshot.sourceMessageCount,
                )
            } else {
                refreshMarketData()
            }
        } catch (_: Throwable) {
            uiState = ComparisonDetailUiState.Error("当前会话暂时无法整理，请稍后重试。")
        }
    }

    private fun refreshMarketData() {
        val snapshot = baseSnapshot ?: return
        val providerSymbols = snapshot.providerSymbols
        if (providerSymbols.isEmpty()) {
            uiState = ComparisonDetailUiState.Content(
                ComparisonContentUi(
                    snapshot = snapshot,
                    refreshPhase = ComparisonRefreshPhase.SESSION_ONLY,
                    refreshedCount = 0,
                    completedCount = 0,
                    refreshTargetCount = 0,
                )
            )
            return
        }

        val requestToken = ++refreshToken
        uiState = ComparisonDetailUiState.Refreshing(
            ComparisonContentUi(
                snapshot = snapshot,
                refreshPhase = ComparisonRefreshPhase.REFRESHING,
                refreshedCount = 0,
                completedCount = 0,
                refreshTargetCount = providerSymbols.size,
            )
        )

        comparisonDataSource.refresh(snapshot) { result ->
            if (requestToken != refreshToken) {
                return@refresh
            }
            val refreshPhase = when {
                result.requestedCount == 0 -> ComparisonRefreshPhase.SESSION_ONLY
                result.isComplete -> ComparisonRefreshPhase.CURRENT
                result.isPartial -> ComparisonRefreshPhase.PARTIAL
                else -> ComparisonRefreshPhase.FAILED
            }
            uiState = ComparisonDetailUiState.Content(
                ComparisonContentUi(
                    snapshot = result.snapshot,
                    refreshPhase = refreshPhase,
                    refreshedCount = result.refreshedCount,
                    completedCount = result.requestedCount,
                    refreshTargetCount = result.requestedCount,
                )
            )
        }
    }

    private fun openStockDetail(row: ConversationStockComparisonRow) {
        val symbol = row.providerSymbol.ifBlank { row.symbol }.trim()
        if (symbol.isBlank()) {
            return
        }
        val params = JSONObject()
        params.put(STOCK_DETAIL_SYMBOL_PARAM, symbol)
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            STOCK_DETAIL_PAGE_NAME,
            params,
        )
    }

    private fun closePage() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }

    private companion object {
        const val STOCK_DETAIL_PAGE_NAME = "stock_detail"
        const val STOCK_DETAIL_SYMBOL_PARAM = "symbol"
        const val HEADER_HEIGHT = 68f
        const val SUMMARY_HEIGHT = 142f
        const val REFRESH_STATUS_HEIGHT = 42f
        const val TABLE_SECTION_HEADER_HEIGHT = 42f
        const val RISK_NOTICE_HEIGHT = 58f
        const val NON_TABLE_CONTENT_HEIGHT = 342f
        const val MIN_TABLE_HEIGHT = 180f
    }
}
